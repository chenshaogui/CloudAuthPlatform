package io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;

public class SM3Util {

    /**
     * 生成盐
     */
    public static byte[] getSalt() {
        SecureRandom random = new SecureRandom();
        byte bytes1[] = new byte[16];
        random.nextBytes(bytes1);
        return bytes1;
    }

    /*
     * 拼接buf1和buf2数组
     */
    public static byte[] arraycat(byte[] buf1, byte[] buf2) {
        byte[] bufret = null;
        int len1 = 0;
        int len2 = 0;
        if (buf1 != null)
            len1 = buf1.length;
        if (buf2 != null)
            len2 = buf2.length;
        if (len1 + len2 > 0)
            bufret = new byte[len1 + len2];
        if (len1 > 0)
            System.arraycopy(buf1, 0, bufret, 0, len1);
        if (len2 > 0)
            System.arraycopy(buf2, 0, bufret, len1, len2);
        return bufret;
    }

    /**
     * 生成哈希值
     *
     * @param data
     * @return
     */
    public static byte[] hash(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return hash;
    }

    /**
     * 生成HMAC值
     *
     * @param key
     * @param data
     * @return
     */
    public static byte[] hmac(byte[] key, byte[] data) {
        KeyParameter keyParameter = new KeyParameter(key);
        SM3Digest digest = new SM3Digest();
        HMac mac = new HMac(digest);
        mac.init(keyParameter);
        mac.update(data, 0, data.length);
        byte[] result = new byte[mac.getMacSize()];
        mac.doFinal(result, 0);
        return result;
    }
}
