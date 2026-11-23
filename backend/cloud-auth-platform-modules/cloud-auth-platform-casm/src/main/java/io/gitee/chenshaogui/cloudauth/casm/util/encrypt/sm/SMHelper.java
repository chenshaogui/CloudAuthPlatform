package io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.SM4;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

public class SMHelper {
	private static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * SM4加密
	 * @param key UTF8编码后的长度必须为128位，即16个英文字符
	 * @param text 原文
	 * @return
	 */
	public static String sm4Encrypt(String key, String text) {
    	try {
	    	byte[] data = SM4Util.encryptEbc(key.getBytes(DEFAULT_ENCODING), text.getBytes(DEFAULT_ENCODING));
	    	return HexUtil.encodeHexStr(data);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	public static String sm4EncryptEcb(String key, String text) {
		try {
			SM4 sm4 = new SM4(Mode.ECB, Padding.PKCS5Padding, HexUtil.decodeHex(key));
			return sm4.encryptHex(text);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * SM4解密
	 * @param key 与SM4加密时使用的值一致
	 * @param cipherText SM4加密后的文本
	 * @return
	 */
    public static String sm4Decrypt(String key, String cipherText) {
    	try {
	    	byte[] data = SM4Util.decryptEbc(key.getBytes(DEFAULT_ENCODING), HexUtil.decodeHex(cipherText));
	    	return new String(data, DEFAULT_ENCODING);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
	 * SM4加密(CBC)
	 * @param key UTF8编码后的长度必须为128位，即16个英文字符
	 * @param text 原文
	 * @param iv
	 * @return
	 */
	public static String sm4Encrypt(String key, String text, String iv) {
    	try {
	    	byte[] data = SM4Util.encryptCbc(key.getBytes(DEFAULT_ENCODING), iv.getBytes(DEFAULT_ENCODING), text.getBytes(DEFAULT_ENCODING));
	    	return HexUtil.encodeHexStr(data);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	/**
	 * SM4解密(CBC)
	 * @param key 与SM4加密时使用的值一致
	 * @param cipherText SM4加密后的文本
	 * @param iv
	 * @return
	 */
    public static String sm4Decrypt(String key, String cipherText, String iv) {
    	try {
	    	byte[] data = SM4Util.decryptCbc(key.getBytes(DEFAULT_ENCODING), iv.getBytes(DEFAULT_ENCODING), HexUtil.decodeHex(cipherText));
	    	return new String(data, DEFAULT_ENCODING);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * SM3生成哈希值
     * @param text 原文
     * @return
     */
    public static String sm3Hash(String text) {
		try {
			byte[] hash = SM3Util.hash(text.getBytes(DEFAULT_ENCODING));
			return HexUtil.encodeHexStr(hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * SM3检验哈希值
     * @param text 原文
     * @param hash 哈希值
     * @return
     */
    public static boolean sm3verify(String text, String hash) {
    	return hash.equals(sm3Hash(text));
    }

    /**
     * SM2获取私钥与公钥
     * @return
     */
    public static SM2Keys sm2GenerateKeys() {
    	AsymmetricCipherKeyPair keyPair = SM2Util.generateKeyPair();
    	return new SM2Keys(keyPair);
    }

    /**
     * SM2使用公钥加密
     * @param publicKey 公钥
     * @param text 原文
     * @return
     */
    public static String sm2Encrypt(String publicKey, String text) {
    	return sm2Encrypt(SM2Keys.toPublicKeyParams(publicKey), text);
    }

    /**
     * SM2使用公钥加密
     * @param publicKeyParams 公钥
     * @param text
     * @return
     */
    public static String sm2Encrypt(ECPublicKeyParameters publicKeyParams, String text) {
    	try {
	        byte[] encData = SM2Util.encrypt(publicKeyParams, text.getBytes(DEFAULT_ENCODING));
	        return HexUtil.encodeHexStr(encData);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * SM2使用私钥解密
     * @param privateKey 私钥
     * @param cipherText SM2加密后的文本
     * @return
     */
    public static String sm2Decrypt(String privateKey, String cipherText) {
    	return sm2Decrypt(SM2Keys.toPrivateKeyParams(privateKey), cipherText);
    }

	/**
	 * SM2使用私钥解密 新标准 C1C3C2
	 * @param privateKey
	 * @param cipherText
	 * @return
	 */
	public static String sm2DecryptNewMode(String privateKey, String cipherText) {
		try {
			byte[] decData = SM2Util.decrypt(SM2Keys.toPrivateKeyParams(privateKey), HexUtil.decodeHex(cipherText), true);
			return new String(decData, DEFAULT_ENCODING);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    /**
     * SM2使用私钥解密
     * @param privateKeyParams 私钥
     * @param cipherText SM2加密后的文本
     * @return
     */
    public static String sm2Decrypt(ECPrivateKeyParameters privateKeyParams, String cipherText) {
    	try {
	        byte[] decData = SM2Util.decrypt(privateKeyParams, HexUtil.decodeHex(cipherText), false);
	        return new String(decData, DEFAULT_ENCODING);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * SM2使用私钥签名
     * @param privateKey 私钥
     * @param text 原文
     * @return
     */
    public static String sm2Sign(String privateKey, String text) {
    	return sm2Sign(SM2Keys.toPrivateKeyParams(privateKey), text);
    }

    /**
     * SM2使用私钥签名
     * @param privateKeyParams 私钥
     * @param text 原文
     * @return
     */
    public static String sm2Sign(ECPrivateKeyParameters privateKeyParams, String text) {
    	try {
	        byte[] signData = SM2Util.sign(privateKeyParams, null, text.getBytes(DEFAULT_ENCODING));
	        return HexUtil.encodeHexStr(signData);
    	} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * SM2使用公钥校验签名
     * @param publicKey 公钥
     * @param text 原文
     * @param sign 签名
     * @return
     */
    public static boolean sm2Verify(String publicKey, String text, String sign) {
    	return sm2Verify(SM2Keys.toPublicKeyParams(publicKey), text, sign);
    }

    /**
     * SM2使用公钥校验签名
     * @param publicKeyParams 公钥
     * @param text 原文
     * @param sign 签名
     * @return
     */
    public static boolean sm2Verify(ECPublicKeyParameters publicKeyParams, String text, String sign) {
    	try {
	        return SM2Util.verify(publicKeyParams, null, text.getBytes(DEFAULT_ENCODING), HexUtil.decodeHex(sign));
	    } catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
}
