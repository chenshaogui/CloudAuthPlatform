package io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm;

import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.pqc.legacy.math.linearalgebra.ByteUtils;


public class SMDemo {

	public static void main(String[] args) throws Exception {
		testSM4();
		testSM3();
		testSM2();

		String text1 = "文本ab12";
		String text2 = "文本ab12文本ab12文本ab12文本ab12文本ab12";
		String text3 = "文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12";
		String text4 = "文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12" +
				"文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12" +
				"文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12" +
				"文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12" +
				"文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12文本ab12";
		int testTimes = 1000;
		testSM4(text1, testTimes);
		testSM4(text2, testTimes);
		testSM4(text3, testTimes);
		testSM4(text4, testTimes);

		testSM3(text1, testTimes);
		testSM3(text2, testTimes);
		testSM3(text3, testTimes);
		testSM3(text4, testTimes);

		testTimes = 100;
		testSM2(text1, testTimes);
		testSM2(text2, testTimes);
		testSM2(text3, testTimes);
		testSM2(text4, testTimes);
	}

	private static void testSM4() throws Exception {
		System.out.println("----SM4测试开始----");

		String text = "加密文本abc";
		String key = "abcdefgh12345678";
		System.out.println("原文：" + text);
		System.out.println("密码：" + key);
		String eText = SMHelper.sm4Encrypt(key, text);
		String dText = SMHelper.sm4Decrypt(key, eText);
		System.out.println("EBC加密信息:" + eText);
		if (!dText.equals(text)) {
			throw new Exception("SM4 EBC 解密不对。。。");
		}

		String iv = "ivabckefg1234567";
		System.out.println("IV：" + iv);
		eText = SMHelper.sm4Encrypt(key, text, iv);
		dText = SMHelper.sm4Decrypt(key, eText, iv);
		System.out.println("CBC加密信息:" + eText);
		if (!dText.equals(text)) {
			throw new Exception("SM4 CBC 解密不对。。。");
		}
		System.out.println("----SM4测试结束----\n");
	}

	private static void testSM4(String text, int times) throws Exception {
		System.out.println("----SM4性能测试开始----");

		String key = "abcdefgh12345678";
		String iv = "ivabckefg1234567";
		System.out.println("原文：" + text);
		System.out.println("密码：" + key);
		System.out.println("IV：" + iv);

		int textLen = text.getBytes("utf-8").length;
		String eText = null;
		long t = System.currentTimeMillis();
		for (int i = 0; i < times; i++) {
			eText = SMHelper.sm4Encrypt(key, text);
			SMHelper.sm4Decrypt(key, eText);
		}
		System.out.println("SM4信息长度：" + textLen + "字节，" + times + "次，EBC加密与解密平均耗时：" + ((double)(System.currentTimeMillis() - t) / times) + "毫秒");

		t = System.currentTimeMillis();
		for (int i = 0; i < times; i++) {
			eText = SMHelper.sm4Encrypt(key, text, iv);
			SMHelper.sm4Decrypt(key, eText, iv);
		}
		System.out.println("SM4信息长度：" + textLen + "字节，" + times + "次，CBC加密与解密平均耗时：" + ((double)(System.currentTimeMillis() - t) / times) + "毫秒");

		System.out.println("----SM4性能测试结束----\n");
	}

	private static void testSM3() throws Exception {
		System.out.println("----SM3测试开始----");

		String text = "加密文本abc";
		String hText = SMHelper.sm3Hash(text);
		System.out.println("原文：" + text);
		System.out.println("哈希值：" + hText);
		if (!SMHelper.sm3verify(text, hText)) {
			throw new Exception("SM3 哈希校验不通过。。。");
		}

		System.out.println("----SM3测试结束----\n");
	}

	private static void testSM3(String text, int times) throws Exception {
		System.out.println("----SM3性能测试开始----");
		System.out.println("原文：" + text);
		int textLen = text.getBytes("utf-8").length;
		String hText = null;
		long t = System.currentTimeMillis();
		for (int i = 0; i < times; i++) {
			hText = SMHelper.sm3Hash(text);
			SMHelper.sm3verify(text, hText);
		}
		System.out.println("SM3信息长度：" + textLen + "字节，" + times + "次，哈希值生成与校验平均耗时：" + ((double)(System.currentTimeMillis() - t) / times) + "毫秒");

		System.out.println("----SM3性能测试结束----\n");
	}

	private static void testSM2() throws Exception {
		System.out.println("----SM2测试开始----");

		String text = "加密文本abc";
		System.out.println("原文：" + text);
		SM2Keys keys = SMHelper.sm2GenerateKeys();

		System.out.println("--16进制简短密钥测试--");
		System.out.println("HEX私钥:" + keys.getPrivateKey());
		System.out.println("HEX公钥:" + keys.getPublicKey());
		String eText = SMHelper.sm2Encrypt(keys.getPublicKey(), text);
		String dText = SMHelper.sm2Decrypt(keys.getPrivateKey(), eText);
		System.out.println("公钥加密信息:" + eText);
		if (!dText.equals(text)) {
			throw new Exception("SM2 解密不对。。。");
		}
		String sText = SMHelper.sm2Sign(keys.getPrivateKey(), text);
		System.out.println("私钥签名信息:" + sText);
		if (!SMHelper.sm2Verify(keys.getPublicKey(), text, sText)) {
			throw new Exception("SM2 签名校验不通过。。。");
		}

		System.out.println("\n--DER密钥测试--");
		System.out.println("DER私钥:" + ByteUtils.toHexString(keys.getDerPrivateKey()));
		System.out.println("DER公钥:" + ByteUtils.toHexString(keys.getDerPublicKey()));
		//验证der格式转换后的密钥是否正确
		ECPublicKeyParameters pubKeyParams = SM2Keys.derToPublicKeyParams(keys.getDerPublicKey());
		ECPrivateKeyParameters priKeyParams = SM2Keys.derToPrivateKeyParams(keys.getDerPrivateKey());
		eText = SMHelper.sm2Encrypt(pubKeyParams, text);
		dText = SMHelper.sm2Decrypt(priKeyParams, eText);
		System.out.println("公钥加密信息:" + eText);
		if (!dText.equals(text)) {
			throw new Exception("SM2 解密不对。。。");
		}
		sText = SMHelper.sm2Sign(priKeyParams, text);
		System.out.println("私钥签名信息:" + sText);
		if (!SMHelper.sm2Verify(pubKeyParams, text, sText)) {
			throw new Exception("SM2 签名校验不通过。。。");
		}


		System.out.println("\n--PEM密钥测试--");
		System.out.println("PEM私钥:" + keys.getPemPrivateKey());
		System.out.println("PEM公钥:" + keys.getPemPublicKey());
		//验证pem格式转换后的密钥是否正确
		pubKeyParams = SM2Keys.pemToPublicKeyParams(keys.getPemPublicKey());
		priKeyParams = SM2Keys.pemToPrivateKeyParams(keys.getPemPrivateKey());
		eText = SMHelper.sm2Encrypt(pubKeyParams, text);
		dText = SMHelper.sm2Decrypt(priKeyParams, eText);
		System.out.println("公钥加密信息:" + eText);
		if (!dText.equals(text)) {
			throw new Exception("SM2 解密不对。。。");
		}
		sText = SMHelper.sm2Sign(priKeyParams, text);
		System.out.println("私钥签名信息:" + sText);
		if (!SMHelper.sm2Verify(pubKeyParams, text, sText)) {
			throw new Exception("SM2 签名校验不通过。。。");
		}

		System.out.println("----SM2测试结束----\n");
	}

	private static void testSM2(String text, int times) throws Exception {
		System.out.println("----SM2性能测试开始----");

		SM2Keys keys = SMHelper.sm2GenerateKeys();
		System.out.println("原文：" + text);
		System.out.println("私钥:" + keys.getPrivateKey());
		System.out.println("公钥:" + keys.getPublicKey());

		int textLen = text.getBytes("utf-8").length;
		String eText = null;
		long t = System.currentTimeMillis();
		for (int i = 0; i < times; i++) {
			eText = SMHelper.sm2Encrypt(keys.getPublicKey(), text);
			SMHelper.sm2Decrypt(keys.getPrivateKey(), eText);
		}
		System.out.println("SM2信息长度：" + textLen + "字节，" + times + "次，非对称加密与解密平均耗时：" + ((double)(System.currentTimeMillis() - t) / times) + "毫秒");

		t = System.currentTimeMillis();
		for (int i = 0; i < times; i++) {
			eText = SMHelper.sm2Sign(keys.getPrivateKey(), text);
			SMHelper.sm2Verify(keys.getPublicKey(), text, eText);
		}
		System.out.println("SM2信息长度：" + textLen + "字节，" + times + "次，签名生成与校验平均耗时：" + ((double)(System.currentTimeMillis() - t) / times) + "毫秒");

		System.out.println("----SM2性能测试结束----\n");
	}
}
