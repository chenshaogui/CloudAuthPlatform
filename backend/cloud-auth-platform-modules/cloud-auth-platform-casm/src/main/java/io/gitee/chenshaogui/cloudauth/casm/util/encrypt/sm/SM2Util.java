package io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;

public class SM2Util {
    private static ECDomainParameters domainParams;
    /**
     * 获取ECDomainParameters
     * @return
     */
    public static ECDomainParameters getDomainParams() {
    	if (domainParams == null) {
    		BigInteger ecc_p = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF", 16);
        	BigInteger ecc_a = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC", 16);
        	BigInteger ecc_b = new BigInteger("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93", 16);
        	BigInteger ecc_n = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16);
        	BigInteger ecc_gx = new BigInteger("32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7", 16);
        	BigInteger ecc_gy = new BigInteger("BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0", 16);
        	ECCurve curve = new ECCurve.Fp(ecc_p, ecc_a, ecc_b, null, null);
        	ECPoint curve_g = curve.createPoint(ecc_gx, ecc_gy);
        	domainParams = new ECDomainParameters(curve, curve_g, ecc_n, BigInteger.ONE);
    	}
    	return domainParams;
    }

    /**
     * 生成密钥对
     * @return
     */
    public static AsymmetricCipherKeyPair generateKeyPair() {
    	ECKeyGenerationParameters ekgParams = new ECKeyGenerationParameters(getDomainParams(), new SecureRandom());
    	ECKeyPairGenerator keyGen = new ECKeyPairGenerator();
    	keyGen.init(ekgParams);
    	return keyGen.generateKeyPair();
    }

    /**
     * 公钥加密
     * @param pubKey
     * @param data
     * @return
     * @throws CryptoException
     */
    public static byte[] encrypt(ECPublicKeyParameters pubKey, byte[] data) throws CryptoException {
        SM2Engine engine = new SM2Engine();
        ParametersWithRandom pwr = new ParametersWithRandom(pubKey, new SecureRandom());
        engine.init(true, pwr);
        return engine.processBlock(data, 0, data.length);
    }

    /**
     * 私钥解密
     * @param priKey newMode=true为新标准
     * @param cipherData
     * @return
     * @throws CryptoException
     */
    public static byte[] decrypt(ECPrivateKeyParameters priKey, byte[] cipherData, boolean newMode) throws CryptoException {
        SM2Engine engine = null;
        if(newMode){
            engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        }else{
            engine = new SM2Engine(SM2Engine.Mode.C1C2C3);
        }
        engine.init(false, priKey);
        return engine.processBlock(cipherData, 0, cipherData.length);
    }

    /**
     * 私钥签名
     * @param priKey
     * @param data
     * @return
     * @throws CryptoException
     */
    public static byte[] sign(ECPrivateKeyParameters priKey, byte[] data) throws CryptoException {
        return sign(priKey, null, data);
    }

    /**
     * 私钥签名
     * @param priKey
     * @param withId
     * @param data
     * @return
     * @throws CryptoException
     */
    public static byte[] sign(ECPrivateKeyParameters priKey, byte[] withId, byte[] data) throws CryptoException {
        SM2Signer signer = new SM2Signer();
        CipherParameters param = null;
        ParametersWithRandom pwr = new ParametersWithRandom(priKey, new SecureRandom());
        if (withId != null) {
            param = new ParametersWithID(pwr, withId);
        } else {
            param = pwr;
        }
        signer.init(true, param);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    /**
     * 公钥验证签名
     * @param pubKey
     * @param data
     * @param sign
     * @return
     */
    public static boolean verify(ECPublicKeyParameters pubKey, byte[] data, byte[] sign) {
        return verify(pubKey, null, data, sign);
    }

    /**
     * 公钥验证签名
     * @param pubKey
     * @param withId
     * @param data
     * @param sign
     * @return
     */
    public static boolean verify(ECPublicKeyParameters pubKey, byte[] withId, byte[] data, byte[] sign) {
        SM2Signer signer = new SM2Signer();
        CipherParameters param = null;
        if (withId != null) {
            param = new ParametersWithID(pubKey, withId);
        } else {
            param = pubKey;
        }
        signer.init(false, param);
        signer.update(data, 0, data.length);
        return signer.verifySignature(sign);
    }
}
