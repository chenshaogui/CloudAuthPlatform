package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SSO 跳转参数加密工具（与认证中心前端 securitySystem 的 utils/ssoQuery.ts 配套）。
 *
 * <p>把 {@code #/ssologin?client=xxx&redirect=yyy} 的明文 query 整体收进一个
 * {@code ?auth_request=xxx} 密文参数，避免 clientId、回跳地址在地址栏 / 日志中明文暴露。</p>
 *
 * <p>密文格式：{@code Base64URL( IV(16) + AES-CTR-NoPadding( flags(1) + 参数数据 ) )}，
 * AES key = {@code SHA256(密钥)} 前 16 字节，IV 每次随机。
 * CTR 为流式模式，无需填充（既避免 padding oracle 风险，也缩短密文）；
 * 参数数据加密前先做紧凑序列化（短键名 + 分隔符 KV + URL 前缀字典）与
 * raw DEFLATE 压缩（压缩后更短才启用），前端按同一规则还原。</p>
 *
 * <p>密钥读取 {@code sa-token.sso-param-key}，未配置时使用
 * {@link #DEFAULT_SECRET}（前端 env 的 {@code VITE_APP_SSO_PARAM_KEY} 必须与其一致）。
 * 全局开关 {@code sa-token.sso-encrypt-param=true} 可让 {@code getSsoAuthUrl} 自动加密，
 * 无需业务方改代码。</p>
 *
 * <p><b>适用边界</b>：仅加密 hash 登录页 URL（auth-url 指向
 * {@code index.html#/ssologin}）。若 auth-url 指向服务端 {@code /sso/auth}，
 * 其 client/redirect/sign 由服务端验签，加密会导致流程中断，此时必须保持明文。</p>
 *
 * @since 1.5.10
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SsoParamCrypto {

    /** 密文参数名 */
    public static final String SSO_QUERY_KEY = "auth_request";

    /** 默认密钥，与前端 ssoQuery.ts 的 DEFAULT_SSO_PARAM_KEY 一致；上线建议通过配置覆盖 */
    public static final String DEFAULT_SECRET = "AesKeyForSso2026";

    /** 流式加密，无填充；与前端 CryptoJS.mode.CTR / pad.NoPadding 一致 */
    private static final String CIPHER_TRANSFORMATION = "AES/CTR/NoPadding";

    private static final int IV_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 短键名别名表（与前端 ssoQuery.ts 保持一致） */
    private static final Map<String, String> LONG_TO_SHORT = new HashMap<>();
    private static final Map<String, String> SHORT_TO_LONG = new HashMap<>();

    static {
        LONG_TO_SHORT.put("client", "c");
        LONG_TO_SHORT.put("redirect", "r");
        LONG_TO_SHORT.put("clientLoginUrl", "cl");
        LONG_TO_SHORT.put("back", "b");
        LONG_TO_SHORT.put("timestamp", "ts");
        LONG_TO_SHORT.put("nonce", "n");
        LONG_TO_SHORT.put("sign", "s");
        for (Map.Entry<String, String> entry : LONG_TO_SHORT.entrySet()) {
            SHORT_TO_LONG.put(entry.getValue(), entry.getKey());
        }
    }

    /** 紧凑 KV 格式：键值对分隔符 / 键值分隔符 */
    private static final String PAIR_SEP = "\u001f";
    private static final String KV_SEP = "\u001e";
    /** URL 前缀字典标记：把常见的协议头压成 1 字节 */
    private static final String PREFIX_HTTPS = "\u0001";
    private static final String PREFIX_HTTP = "\u0002";

    /** flags 位：1=raw DEFLATE 压缩，2=使用短键名，4=回退 JSON 格式 */
    private static final int FLAG_DEFLATE = 1;
    private static final int FLAG_SHORT_KEYS = 2;
    private static final int FLAG_JSON = 4;

    /** 读取当前生效的密钥 */
    public static String resolveSecret() {
        return CloudPlatformHelper.getValue("sa-token.sso-param-key", DEFAULT_SECRET);
    }

    /** SHA256(密钥) 前 16 字节作为 AES-128 密钥 */
    private static byte[] deriveKey(String secret) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(hash, IV_LENGTH);
    }

    private static Cipher createCipher(int mode, String secret, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(deriveKey(secret), "AES"), new IvParameterSpec(iv));
        return cipher;
    }

    /** 加密参数对象为 URL 安全的密文串 */
    public static String encryptParams(Map<String, String> params) {
        return encryptParams(params, resolveSecret());
    }

    /**
     * 加密参数对象为 URL 安全的密文串
     *
     * @param params 参数（按插入顺序序列化）
     * @param secret 密钥，需与前端一致
     */
    public static String encryptParams(Map<String, String> params, String secret) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            byte[] cipherBytes = createCipher(Cipher.ENCRYPT_MODE, secret, iv).doFinal(serializeParams(params));
            byte[] combined = new byte[IV_LENGTH + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(cipherBytes, 0, combined, IV_LENGTH, cipherBytes.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("SSO 跳转参数加密失败", e);
        }
    }

    /** 解密密文串为参数对象（供联调 / 服务端校验使用） */
    public static Map<String, String> decryptParams(String token) {
        return decryptParams(token, resolveSecret());
    }

    /** 解密密文串为参数对象 */
    public static Map<String, String> decryptParams(String token, String secret) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(token);
            if (combined.length <= IV_LENGTH) {
                return new LinkedHashMap<>();
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            return deserializeParams(createCipher(Cipher.DECRYPT_MODE, secret, iv).doFinal(cipherBytes));
        } catch (GeneralSecurityException | DataFormatException | IOException | IllegalArgumentException e) {
            throw new IllegalStateException("SSO 跳转参数解密失败", e);
        }
    }

    /**
     * 参数序列化：短键名 + 紧凑 KV + URL 前缀字典，必要时回退 JSON；
     * 压缩后更短才启用 raw DEFLATE。返回 {@code flags(1) + 数据}。
     */
    private static byte[] serializeParams(Map<String, String> params) throws IOException {
        boolean shortKeys = canUseShortKeys(params);
        Map<String, String> mapped = mapKeys(params, shortKeys);
        int flags = shortKeys ? FLAG_SHORT_KEYS : 0;
        String text;
        if (hasConflictChars(mapped)) {
            text = JsonUtils.toJsonString(mapped);
            flags |= FLAG_JSON;
        } else {
            text = buildCompactText(mapped);
        }
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        byte[] zipped = deflateRaw(raw);
        boolean compressed = zipped.length < raw.length;
        if (compressed) {
            flags |= FLAG_DEFLATE;
        }
        return packBody(flags, compressed ? zipped : raw);
    }

    /** 原始键名若与短名冲突（例如本来就有 c / r），整体不启用短名，避免解密歧义 */
    private static boolean canUseShortKeys(Map<String, String> params) {
        for (String key : params.keySet()) {
            if (SHORT_TO_LONG.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> mapKeys(Map<String, String> params, boolean shortKeys) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = shortKeys ? LONG_TO_SHORT.getOrDefault(entry.getKey(), entry.getKey()) : entry.getKey();
            mapped.put(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return mapped;
    }

    /** 值本身含控制字符时回退 JSON，保证可逆 */
    private static boolean hasConflictChars(Map<String, String> mapped) {
        for (Map.Entry<String, String> entry : mapped.entrySet()) {
            if (hasConflictChar(entry.getKey()) || hasConflictChar(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConflictChar(String value) {
        return value.contains(PAIR_SEP)
                || value.contains(KV_SEP)
                || value.startsWith(PREFIX_HTTPS)
                || value.startsWith(PREFIX_HTTP);
    }

    private static String buildCompactText(Map<String, String> mapped) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : mapped.entrySet()) {
            if (sb.length() > 0) {
                sb.append(PAIR_SEP);
            }
            sb.append(entry.getKey()).append(KV_SEP).append(encodeUrlPrefix(entry.getValue()));
        }
        return sb.toString();
    }

    private static String encodeUrlPrefix(String value) {
        if (value.startsWith("https://")) {
            return PREFIX_HTTPS + value.substring(8);
        }
        if (value.startsWith("http://")) {
            return PREFIX_HTTP + value.substring(7);
        }
        return value;
    }

    private static String decodeUrlPrefix(String value) {
        if (value.startsWith(PREFIX_HTTPS)) {
            return "https://" + value.substring(1);
        }
        if (value.startsWith(PREFIX_HTTP)) {
            return "http://" + value.substring(1);
        }
        return value;
    }

    private static byte[] packBody(int flags, byte[] data) {
        byte[] body = new byte[data.length + 1];
        body[0] = (byte) flags;
        System.arraycopy(data, 0, body, 1, data.length);
        return body;
    }

    /** 反序列化：解压 → 解析紧凑 KV/JSON → 还原短键名与 URL 前缀 */
    private static Map<String, String> deserializeParams(byte[] body) throws DataFormatException, IOException {
        if (body.length < 2) {
            return new LinkedHashMap<>();
        }
        int flags = body[0] & 0xff;
        byte[] data = Arrays.copyOfRange(body, 1, body.length);
        if ((flags & FLAG_DEFLATE) != 0) {
            data = inflateRaw(data);
        }
        String text = new String(data, StandardCharsets.UTF_8);
        Map<String, String> parsed = (flags & FLAG_JSON) != 0 ? parseJson(text) : parseCompact(text);
        return restoreKeys(parsed, flags);
    }

    private static Map<String, String> parseJson(String text) {
        Map<String, String> obj = JsonUtils.parseObject(text, new TypeReference<LinkedHashMap<String, String>>() {
        });
        return obj == null ? new LinkedHashMap<>() : obj;
    }

    private static Map<String, String> parseCompact(String text) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : text.split(PAIR_SEP, -1)) {
            int idx = pair.indexOf(KV_SEP);
            if (!pair.isEmpty() && idx >= 0) {
                parsed.put(pair.substring(0, idx), decodeUrlPrefix(pair.substring(idx + 1)));
            }
        }
        return parsed;
    }

    private static Map<String, String> restoreKeys(Map<String, String> parsed, int flags) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            String key = (flags & FLAG_SHORT_KEYS) != 0
                    ? SHORT_TO_LONG.getOrDefault(entry.getKey(), entry.getKey())
                    : entry.getKey();
            result.put(key, entry.getValue());
        }
        return result;
    }

    /** raw DEFLATE，与前端 pako.deflateRaw 一致（无 zlib 头尾） */
    private static byte[] deflateRaw(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(data.length)) {
            deflater.setInput(data);
            deflater.finish();
            byte[] buf = new byte[256];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** raw INFLATE，与前端 pako.inflateRaw 一致 */
    private static byte[] inflateRaw(byte[] data) throws DataFormatException, IOException {
        Inflater inflater = new Inflater(true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2)) {
            inflater.setInput(data);
            byte[] buf = new byte[256];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("DEFLATE 数据不完整");
                }
                if (n > 0) {
                    out.write(buf, 0, n);
                }
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    /**
     * 把 URL 中原有的明文 query 收进一个 {@code auth_request} 密文参数，保留 URL 前缀（含 hash 路径）。
     *
     * <p>例：{@code https://x/index.html#/ssologin?client=a&redirect=b}
     * → {@code https://x/index.html#/ssologin?auth_request=Base64URL...}</p>
     *
     * @param url sa-token 生成的原始跳转 URL
     * @return 参数加密后的 URL；无 query 时原样返回
     */
    public static String encryptSsoUrl(String url) {
        return encryptSsoUrl(url, resolveSecret());
    }

    /** 指定密钥版本，便于独立测试 */
    public static String encryptSsoUrl(String url, String secret) {
        if (url == null || url.indexOf('?') < 0) {
            return url;
        }
        // hash 路由下 query 位于 # 之后，优先从 # 之后开始找，避免被路径上的 ? 干扰
        int hashIdx = url.indexOf('#');
        int idx = hashIdx >= 0 ? url.indexOf('?', hashIdx) : url.indexOf('?');
        if (idx < 0) {
            return url;
        }
        String base = url.substring(0, idx);
        String query = url.substring(idx + 1);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (!pair.isEmpty()) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                params.put(percentDecode(key), percentDecode(value));
            }
        }
        if (params.isEmpty()) {
            return url;
        }
        return base + "?" + SSO_QUERY_KEY + "=" + encryptParams(params, secret);
    }

    /**
     * 按 UTF-8 解码 %XX，并把裸 '+' 还原为空格（与 vue-router parseQuery 的 decode 口径一致，
     * 保证密文解密后 route.query 中的值与旧明文链接完全相同）。
     */
    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0 && value.indexOf('+') < 0) {
            return value;
        }
        byte[] buffer = new byte[value.length()];
        int len = 0;
        int pos = 0;
        while (pos < value.length()) {
            char c = value.charAt(pos);
            if (isPercentEscape(value, pos)) {
                int hi = Character.digit(value.charAt(pos + 1), 16);
                int lo = Character.digit(value.charAt(pos + 2), 16);
                buffer[len++] = (byte) ((hi << 4) + lo);
                pos += 3;
            } else {
                // 非 %XX 字符按 UTF-8 逐字节写入；'+' 是 URLEncoder 对空格的编码
                byte[] bytes = String.valueOf(c == '+' ? ' ' : c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    buffer[len++] = b;
                }
                pos++;
            }
        }
        return new String(buffer, 0, len, StandardCharsets.UTF_8);
    }

    private static boolean isPercentEscape(String value, int pos) {
        return value.charAt(pos) == '%'
                && pos + 2 < value.length()
                && Character.digit(value.charAt(pos + 1), 16) >= 0
                && Character.digit(value.charAt(pos + 2), 16) >= 0;
    }
}
