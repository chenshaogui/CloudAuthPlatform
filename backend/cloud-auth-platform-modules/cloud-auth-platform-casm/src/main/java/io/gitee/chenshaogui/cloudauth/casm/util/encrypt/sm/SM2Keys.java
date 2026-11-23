package io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.pqc.legacy.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class SM2Keys {
	private static final String ALGO_NAME_EC = "EC";
	private static final String PEM_STRING_PUBLIC = "PUBLIC KEY";
	private static final String PEM_STRING_ECPRIVATEKEY = "EC PRIVATE KEY";
	private static final Provider PROVIDER = new BouncyCastleProvider();
//通过Security.addProvider(new BouncyCastleProvider())添加后的BouncyCastleProvider对象是JVM全局的，不同类加载器环境下会出现同名类转换的错误
//	static {
//		//提供器添加到JDK中
//        Security.addProvider(new BouncyCastleProvider());
//    }

	private ECPrivateKeyParameters privateKeyParams;
	private ECPublicKeyParameters publicKeyParams;

	public SM2Keys(AsymmetricCipherKeyPair keyPair) {
		privateKeyParams = (ECPrivateKeyParameters)keyPair.getPrivate();
    	publicKeyParams = (ECPublicKeyParameters)keyPair.getPublic();
	}

	/**
	 * @return the privateKeyParams
	 */
	public ECPrivateKeyParameters getPrivateKeyParams() {
		return privateKeyParams;
	}

	/**
	 * @return the publicKeyParams
	 */
	public ECPublicKeyParameters getPublicKeyParams() {
		return publicKeyParams;
	}


	/**
	 * 获取简短私钥16进是字符串
	 * @return
	 */
	public String getPrivateKey() {
		return ByteUtils.toHexString(privateKeyParams.getD().toByteArray());
	}

	/**
	 * 获取简短公钥16进是字符串
	 * @return
	 */
	public String getPublicKey() {
		return ByteUtils.toHexString(publicKeyParams.getQ().getEncoded(false));
	}

	/**
	 * 获取Der格式的私钥，返回的二进制信息可以写入到文件中，如：ec.pkcs8.pri.der
	 * @return
	 */
	public byte[] getDerPrivateKey() {
		ECParameterSpec spec = getECParameterSpec();
		BCECPublicKey publicKey = null;
		if (publicKeyParams != null) {
			publicKey = new BCECPublicKey(ALGO_NAME_EC, publicKeyParams, spec, BouncyCastleProvider.CONFIGURATION);
		}
		BCECPrivateKey privateKey = new BCECPrivateKey(ALGO_NAME_EC, privateKeyParams, publicKey, spec, BouncyCastleProvider.CONFIGURATION);
		return privateKey.getEncoded();
	}

	/**
	 * 获取Der格式的公钥，返回的二进制信息可以写入到文件中，如：ec.x509.pub.der
	 * @return
	 */
	public byte[] getDerPublicKey() {
		ECParameterSpec spec = getECParameterSpec();
		BCECPublicKey publicKey = new BCECPublicKey(ALGO_NAME_EC, publicKeyParams, spec, BouncyCastleProvider.CONFIGURATION);
		return publicKey.getEncoded();
	}

	private ECParameterSpec getECParameterSpec() {
		ECDomainParameters domainParams = publicKeyParams.getParameters();
		return new ECParameterSpec(domainParams.getCurve(), domainParams.getG(), domainParams.getN(), domainParams.getH());
	}

	/**
	 * 获取Pem格式的私钥，返回的字符串可以写入到文件中，如：ec.pkcs8.pri.pem
	 * @return
	 */
	public String getPemPrivateKey() {
		return convertDerToPem(PEM_STRING_ECPRIVATEKEY, getDerPrivateKey());
	}

	/**
	 * 获取Pem格式的公钥，返回的字符串可以写入到文件中，如：ec.x509.pub.pem
	 * @return
	 */
	public String getPemPublicKey() {
		return convertDerToPem(PEM_STRING_PUBLIC, getDerPublicKey());
	}

	//DER格式转为PEN格式
	private String convertDerToPem(String type, byte[] encodedData) {
		try {
			ByteArrayOutputStream bOut = new ByteArrayOutputStream();
			PemWriter pWrt = new PemWriter(new OutputStreamWriter(bOut));
			try {
				PemObject pemObj = new PemObject(type, encodedData);
				pWrt.writeObject(pemObj);
			} finally {
				pWrt.close();
			}
			return new String(bOut.toByteArray());
		} catch(IOException e)  {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 16进是字符串私钥转ECPrivateKeyParameters
	 * @param privateKeyStr
	 * @return
	 */
	public static ECPrivateKeyParameters toPrivateKeyParams(String privateKeyStr) {
		//16进制转为BigInteger
		BigInteger biKey = new BigInteger(privateKeyStr, 16);
    	return new ECPrivateKeyParameters(biKey, SM2Util.getDomainParams());
	}

	/**
	 * 16进是字符串公钥转ECPublicKeyParameters
	 * @param publicKeyStr
	 * @return
	 */
	public static ECPublicKeyParameters toPublicKeyParams(String publicKeyStr) {
		ECDomainParameters domainParams = SM2Util.getDomainParams();
		ECPoint point = domainParams.getCurve().decodePoint(ByteUtils.fromHexString(publicKeyStr));
    	return new ECPublicKeyParameters(point, domainParams);
	}

	/**
	 * DER私钥转ECPrivateKeyParameters
	 * @param priKeyPkcs8Der
	 * @return
	 */
	public static ECPrivateKeyParameters derToPrivateKeyParams(byte[] priKeyPkcs8Der) {
		try {
			KeySpec keySpec = new PKCS8EncodedKeySpec(priKeyPkcs8Der);
		    KeyFactory keyFactory = KeyFactory.getInstance(ALGO_NAME_EC, PROVIDER);
		    BCECPrivateKey privateKey = (BCECPrivateKey)keyFactory.generatePrivate(keySpec);

		    ECParameterSpec spec = privateKey.getParameters();
		    ECDomainParameters ecp = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH());
		    return new ECPrivateKeyParameters(privateKey.getD(), ecp);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * DER公钥转ECPublicKeyParameters
	 * @param pubKeyX509Der
	 * @return
	 */
	public static ECPublicKeyParameters derToPublicKeyParams(byte[] pubKeyX509Der) {
		try {
			KeySpec keySpec = new X509EncodedKeySpec(pubKeyX509Der);
			KeyFactory keyFactory = KeyFactory.getInstance(ALGO_NAME_EC, PROVIDER);
			BCECPublicKey publicKey = (BCECPublicKey)keyFactory.generatePublic(keySpec);

			ECParameterSpec spec = publicKey.getParameters();
			ECDomainParameters ecp = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH());
		    return new ECPublicKeyParameters(publicKey.getQ(), ecp);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * PEM私钥转ECPrivateKeyParameters
	 * @param priKeyPkcs8Pem
	 * @return
	 */
	public static ECPrivateKeyParameters pemToPrivateKeyParams(String priKeyPkcs8Pem) {
		byte[] derData = convertPemToDer(PEM_STRING_ECPRIVATEKEY, priKeyPkcs8Pem);
		return derToPrivateKeyParams(derData);
	}

	/**
	 * PEM公钥转ECPublicKeyParameters
	 * @param pubKeyX509Pem
	 * @return
	 */
	public static ECPublicKeyParameters pemToPublicKeyParams(String pubKeyX509Pem) {
		byte[] derData = convertPemToDer(PEM_STRING_PUBLIC, pubKeyX509Pem);
		return derToPublicKeyParams(derData);
	}

	private static byte[] convertPemToDer(String type, String pemStr) {
		try {
			PemReader reader = new PemReader(new StringReader(pemStr));
			PemObject pemObj = reader.readPemObject();
			reader.close();
			if (!type.equals(pemObj.getType())) {
				throw new RuntimeException("公钥类型不对");
			}
			return pemObj.getContent();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
}
