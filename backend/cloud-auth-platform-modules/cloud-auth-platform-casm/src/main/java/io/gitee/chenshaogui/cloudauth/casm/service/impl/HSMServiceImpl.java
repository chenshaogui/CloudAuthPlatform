package io.gitee.chenshaogui.cloudauth.casm.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSONObject;
import io.gitee.chenshaogui.cloudauth.casm.config.SnowflakeIdWorker;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyLevelEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyTypeEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoVo;
import io.gitee.chenshaogui.cloudauth.casm.service.IHSMService;
import io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm.SM2Keys;
import io.gitee.chenshaogui.cloudauth.casm.util.encrypt.sm.SMHelper;
import io.gitee.chenshaogui.cloudauth.common.core.constant.GlobalConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

/**
 * 密钥信息管理Service业务层处理
 *
 * @author JLL
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class HSMServiceImpl implements IHSMService {

    private final RedissonClient redissonClient;

    private final SnowflakeIdWorker snowflake;

    private final static String BASE_KEY = "5e79146068bfe6cbb6bfcaf6fb0a4f8b";

    private final static String ROOT_KEY_CACHE = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_ROOT_KEY");

    private final static String ROOT_KEY_CACHE_HISTORY = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_ROOT_KEY_HISTORY");

    private final static String KEY_PRE = "BASEKEY-";

    private final static String KEY_ID_CACHE = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_KEY:ID_CACHE:");

    private final static String KEY_CACHE = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_KEY:CACHE:");

    private final static String KEY_CACHE_HISTORY = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_KEY:CACHE:HISTORY:");

    private final static String CLIENT_CACHE_SET = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "CLIENT_CACHE_SET");


    private final static String KEY_USE_COUNT = getCacheKey(GlobalConstants.GLOBAL_CASM_KEY, "APP_KEY:CACHE:COUNT:");

    private static ThreadLocal<Long> threadLocal = new ThreadLocal<>();


    /**
     * 获取缓存key
     * @param prefix
     * @param key
     * @return
     */
    private static String getCacheKey(String prefix, String key) {
        return StringUtils.join(prefix, key);
    }

    /**
     * 初始化根密钥
     * @throws Exception
     */
    @Override
    @PostConstruct
    public void initRootKey() throws ServiceException {
        RBucket<KeyInfoVo> rBucket = redissonClient.getBucket(ROOT_KEY_CACHE);
        if (!rBucket.isExists()) {
            KeyInfoVo.KeyInfoVoBuilder builder = KeyInfoVo.builder();
            String key = this.generateKey();
            String salt = RandomUtil.randomString(20);
            String keyId = ROOT_KEY_CACHE + "-" + RandomUtil.randomString(20);
            if (threadLocal.get() != null) {
                builder.prevKeyId(threadLocal.get());
                threadLocal.remove();
            }
            builder.id(snowflake.nextId())
                    .keyId(keyId)
                    .keyLevel(KeyLevelEnum.CORE)
                    .keyAlthm(KeyAlthmEnum.SM4)
                    .keyType(KeyTypeEnum.KEK)
                    .salt(salt)
                    .expireTime(null)
                    .encryptedKey(this.encryptKey(key, null, salt))
                    .keyLevel(KeyLevelEnum.CORE)
                    .kekKeyId(0L);
            KeyInfoVo rootKey = builder.build();
            rBucket.set(rootKey);
        }
    }

    /**
     * 创建密钥
     * @param keyTypeEnum
     * @param althmEnum
     * @param keyId
     * @return
     * @throws Exception
     */
    @Override
    public KeyInfoVo createKey(KeyTypeEnum keyTypeEnum, KeyAlthmEnum althmEnum, String keyId) throws ServiceException {
        Optional<KeyAlthmEnum> optionalAlthmEnumEnum = Optional.of(althmEnum);
        if (!optionalAlthmEnumEnum.isPresent()) {
            // 抛出异常
            throw new ServiceException("密钥算法不能为空", 1003);
        }

        if (keyTypeEnum == null) {
            throw new ServiceException("密钥类型不能为空", 1003);
        }

        if (keyTypeEnum.equals(KeyTypeEnum.PEK) && !althmEnum.equals(KeyAlthmEnum.SM2)) {
            throw new ServiceException("密钥类型为PEK时，密钥算法只能为SM2", 1003);
        }

        KeyInfoVo rootKeyInfo = this.getRootKey();
        String rootKeys = this.decryptKey(rootKeyInfo.getEncryptedKey(), BASE_KEY, rootKeyInfo.getSalt());
        KeyInfoVo.KeyInfoVoBuilder builder = KeyInfoVo.builder();
        String salt = RandomUtil.randomString(20);
        if (StringUtils.isEmpty(keyId)) {
            keyId = KEY_PRE + keyTypeEnum.getType() + "-" + DateUtil.format(new Date(), "yyyyMMddHHmmssSSS") + snowflake.nextId();
        }
        builder.keyId(keyId);
        Date expireTime = DateUtil.date().offset(DateField.DAY_OF_YEAR, RandomUtil.randomInt(1,5)).toJdkDate();
        if (keyTypeEnum.getIsExpire()) {
            expireTime = DateUtil.date().offset(DateField.MINUTE, RandomUtil.randomInt(5, 10)).toJdkDate();
        }
        final Date finalExpireTime = expireTime;
        optionalAlthmEnumEnum.ifPresent(althmEnum1 -> {
            //构造根密钥
            builder.keyAlthm(althmEnum1);
            if (althmEnum1.equals(KeyAlthmEnum.RSA)) {
                throw new ServiceException("暂不支持此算法！");
            } else if (althmEnum1.equals(KeyAlthmEnum.AES)) {
                throw new ServiceException("暂不支持此算法！");
            } else if (althmEnum1.equals(KeyAlthmEnum.SM2)) {
                SM2Keys sm2Keys = SMHelper.sm2GenerateKeys();
                String privateKey = sm2Keys.getPrivateKey();
                String publicKey = sm2Keys.getPublicKey();
                JSONObject keyObject = new JSONObject()
                    .fluentPut("publicKey", publicKey)
                    .fluentPut("privateKey", privateKey);
                builder.id(snowflake.nextId())
                    .keyLevel(KeyLevelEnum.NORMAL)
                    .keyAlthm(KeyAlthmEnum.SM2)
                    .keyType(keyTypeEnum)
                    .salt(salt)
                    .expireTime(finalExpireTime)
                    .encryptedKey(this.encryptKey(keyObject.toJSONString(), rootKeys, salt))
                    .keyLevel(KeyLevelEnum.NORMAL)
                    .kekKeyId(rootKeyInfo.getId());
            } else if (althmEnum1.equals(KeyAlthmEnum.SM4)) {
                String key = this.generateKey();
                builder.id(snowflake.nextId())
                    .keyLevel(KeyLevelEnum.NORMAL)
                    .keyAlthm(KeyAlthmEnum.SM4)
                    .keyType(keyTypeEnum)
                    .salt(salt)
                    .expireTime(finalExpireTime)
                    .encryptedKey(this.encryptKey(key, rootKeys, salt))
                    .keyLevel(KeyLevelEnum.NORMAL)
                    .kekKeyId(rootKeyInfo.getId());
            }
        });
        return builder.build();
    }

    /**
     * 申请密钥
     * @param keyTypeEnum
     * @param althmEnum
     * @param clientId
     * @param poolSize
     * @return
     * @throws Exception
     */
    @Override
    public KeyInfoVo applyKey(KeyTypeEnum keyTypeEnum, KeyAlthmEnum althmEnum, String clientId, Integer poolSize) throws ServiceException {
        RSet<String> rSet = redissonClient.getSet(KEY_ID_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RSet<String> rSetClient = redissonClient.getSet(CLIENT_CACHE_SET);
        if (!rSetClient.contains(clientId)) {
            rSetClient.add(clientId);
        }
        KeyInfoVo keyInfoVo = null;
        if (poolSize == null || poolSize <= 0) {
            poolSize = keyTypeEnum.getDefaultPool();
        }
        if (rSet.size() < poolSize) {
            // 获取密钥
            keyInfoVo = this.createKey(keyTypeEnum, althmEnum, null);
            keyInfoVo.setClientId(clientId);
            rSet.add(String.valueOf(keyInfoVo.getId()));
            rMap.put(String.valueOf(keyInfoVo.getId()), keyInfoVo);
        } else {
            // 获取密钥
            String keyId = rSet.random();
            keyInfoVo = rMap.get(keyId);
            if (keyInfoVo == null) {
                // 获取密钥
                keyInfoVo = this.createKey(keyTypeEnum, althmEnum, null);
                keyInfoVo.setClientId(clientId);
                rSet.add(String.valueOf(keyInfoVo.getId()));
                rMap.put(String.valueOf(keyInfoVo.getId()), keyInfoVo);
                rSet.remove(keyId);
            }
        }
        return keyInfoVo;
    }

    /**
     * 使用密钥
     * @param keyTypeEnum
     * @param keyId
     * @param clientId
     * @return
     * @throws Exception
     */
    @Override
    public KeyInfoVo useKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException {
        // 获取已申请的密钥，并记录使用日志，数据量可能会巨大，需要优化，要分表处理
        RSet<String> rSetClient = redissonClient.getSet(CLIENT_CACHE_SET);
        RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RMap<String, KeyInfoVo> rMapHistory = redissonClient.getMap(KEY_CACHE_HISTORY  + keyTypeEnum.getType() + ":" + clientId);
        if (!rSetClient.contains(clientId)) {
            throw new ServiceException("该应用不可用，请检查！", 1004);
        }
        KeyInfoVo keyInfoVo = rMap.get(keyId);
        if (keyInfoVo == null) {
            keyInfoVo = rMapHistory.get(keyId);
        }
        if (keyInfoVo == null) {
            log.error("{},密钥不存在！", keyId);
            throw new ServiceException("密钥不存在！", 1001);
        }
        RAtomicLong rAtomicLong = redissonClient.getAtomicLong(KEY_USE_COUNT  + keyTypeEnum.getType() + ":" + clientId + ":" + keyId);
        Long cnt = rAtomicLong.incrementAndGet();
        keyInfoVo.setUseCount(cnt);
        return keyInfoVo;
    }

    /**
     * 获取根密钥
     * @return
     * @throws Exception
     */
    @Override
    public KeyInfoVo getRootKey() throws ServiceException {
        RBucket<KeyInfoVo> rBucket = redissonClient.getBucket(ROOT_KEY_CACHE);
        if (!rBucket.isExists()) {
            this.initRootKey();
        }
        KeyInfoVo rootKeyInfo = rBucket.get();
        return rootKeyInfo;
    }

    /**
     *  密钥分发
     * @param keyInfo
     * @return
     * @throws Exception
     */
    @Override
    public void disputeKey(KeyInfoVo keyInfo, String clientId) throws ServiceException {
        // 密钥分发目的在于处理过期的密钥，将新密钥主动推给需方，避免密钥过期，此处可暂不实现
    }

    /**
     * 密钥退役轮换
     * @param keyId
     * @return
     * @throws Exception
     */
    @Override
    public KeyInfoVo rotationKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException {
        if (!keyTypeEnum.getSupportRotation()) {
            throw new ServiceException("该密钥不支持轮换！", 1005);
        }
        RSet<String> rSetClient = redissonClient.getSet(CLIENT_CACHE_SET);
        RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RMap<String, KeyInfoVo> rMapHistory = redissonClient.getMap(KEY_CACHE_HISTORY  + keyTypeEnum.getType() + ":" + clientId);
        if (!rSetClient.contains(clientId)) {
            throw new ServiceException("该应用不可用，请检查！");
        }
        KeyInfoVo keyInfoVo = rMap.get(keyId);
        KeyInfoVo newKeyInfoVo = null;
        if (keyInfoVo != null) {
            newKeyInfoVo = this.createKey(keyInfoVo.getKeyType(), keyInfoVo.getKeyAlthm(), null);
            newKeyInfoVo.setPrevKeyId(keyInfoVo.getId());
            newKeyInfoVo.setRotationTime(new Date());
            keyInfoVo.setLastRotationTime(new Date());
            keyInfoVo.setRotationKeyId(newKeyInfoVo.getId());
            rMap.put(String.valueOf(newKeyInfoVo.getId()), newKeyInfoVo);
            rMap.put(String.valueOf(keyInfoVo.getId()), keyInfoVo);
        } else {
            keyInfoVo = rMapHistory.get(keyId);
            if (keyInfoVo != null) {
                newKeyInfoVo = this.createKey(keyInfoVo.getKeyType(), keyInfoVo.getKeyAlthm(), null);
                newKeyInfoVo.setPrevKeyId(keyInfoVo.getId());
                newKeyInfoVo.setRotationTime(new Date());
                newKeyInfoVo.setClientId(keyInfoVo.getClientId());
                keyInfoVo.setLastRotationTime(new Date());
                keyInfoVo.setRotationKeyId(newKeyInfoVo.getId());
                rMap.put(String.valueOf(newKeyInfoVo.getId()), newKeyInfoVo);
                rMapHistory.put(String.valueOf(keyInfoVo.getId()), keyInfoVo);
            }
        }
        return newKeyInfoVo;
    }

    @Override
    public Boolean distroyKey(KeyTypeEnum keyTypeEnum, String keyId, String clientId) throws ServiceException {
        RSet<String> rSet = redissonClient.getSet(KEY_ID_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + keyTypeEnum.getType() + ":" + clientId);
        RMap<String, KeyInfoVo> rMapHistory = redissonClient.getMap(KEY_CACHE_HISTORY  + keyTypeEnum.getType() + ":" + clientId);
        log.info("销毁密钥：{}", keyId);
        rSet.remove(keyId);
        rMap.remove(keyId);
        rMapHistory.remove(keyId);
        return true;
    }

    /**
     * 密钥验证
     * @param publicKey
     * @param text
     * @param sign
     * @return
     * @throws Exception
     */
    @Override
    public Boolean verify(String publicKey, String text, String sign) throws ServiceException {
        return SMHelper.sm2Verify(publicKey, text, sign);
    }

    /**
     * 重置根密钥
     * @throws Exception
     */
    @Override
    public void resetRootKey() throws ServiceException {
        RBucket<KeyInfoVo> rBucket = redissonClient.getBucket(ROOT_KEY_CACHE);
        try {
            if (rBucket.isExists()) {
                // 移入历史
                KeyInfoVo keyInfoVo = rBucket.get();
                RMap<Long, KeyInfoVo> rMap = redissonClient.getMap(ROOT_KEY_CACHE_HISTORY);
                rMap.put(keyInfoVo.getId(), keyInfoVo);
                threadLocal.set(keyInfoVo.getId());
                rBucket.delete();
                this.initRootKey();
            }
        } catch (Exception e) {
            log.error("重置密钥失败！", e);
        } finally {
            threadLocal.remove();
        }
    }

    /**
     * 密钥过期处理
     * @throws Exception
     */
    @Override
    public void expireKeys() throws ServiceException {
        RSet<String> rSetClient = redissonClient.getSet(CLIENT_CACHE_SET);
        rSetClient.readAll().forEach(clientId -> {
            Arrays.asList(KeyTypeEnum.values()).stream()
                .filter(keyTypeEnum -> keyTypeEnum.getIsExpire()).forEach(keyTypeEnum -> {
                RSet<String> rSet = redissonClient.getSet(KEY_ID_CACHE + keyTypeEnum.getType() + ":" + clientId);
                RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + keyTypeEnum.getType() + ":" + clientId);
                RMap<String, KeyInfoVo> rMapHistory = redissonClient.getMap(KEY_CACHE_HISTORY  + keyTypeEnum.getType() + ":" + clientId);
                rMap.forEach((keyId, keyInfoVo) -> {
                    // 主要处理数据加密密钥
                    if (keyInfoVo.getExpireTime() != null && isExpired(keyInfoVo)) {
                        rMapHistory.put(keyId, keyInfoVo);
                        log.info("移除密钥：{}", keyId);
                        rSet.remove(keyId);
                        rMap.remove(keyId);
                    }
                });
                // 移除历史密钥，过期时间超过两小时的
                rMapHistory.forEach((keyId, keyInfoVo) -> {
                    if (keyInfoVo.getExpireTime() != null && keyInfoVo.getExpireTime().getTime() + 2 * 60 * 60 * 1000 < System.currentTimeMillis()) {
                        log.info("移除历史密钥：{}", keyId);
                        rMapHistory.remove(keyId);
                    }
                });
            });
        });
    }

    /**
     * 转换密钥信息
     * @param keyInfoVo
     * @return
     * @throws Exception
     */

    @Override
    public KeyInfoRspVo transKeyInfo(KeyInfoVo keyInfoVo, String transKeyId) throws ServiceException {
        KeyInfoRspVo keyInfoRspVo = MapstructUtils.convert(keyInfoVo, KeyInfoRspVo.class);
        KeyInfoVo rootKeyInfo = this.getRootKey();
        String rootKeys = this.decryptKey(rootKeyInfo.getEncryptedKey(), BASE_KEY, rootKeyInfo.getSalt());
        String keys = this.decryptKey(keyInfoVo.getEncryptedKey(), rootKeys, keyInfoVo.getSalt());
        if (keyInfoVo.getKeyAlthm().equals(KeyAlthmEnum.SM2)) {
            JSONObject jsonObject = JSONObject.parseObject(keys);
            keys = jsonObject.getString("publicKey");
            RAtomicLong rAtomicLong = redissonClient.getAtomicLong(KEY_USE_COUNT  + keyInfoVo.getKeyType().getType() + ":" + keyInfoVo.getClientId() + ":" + keyInfoVo.getId());
            // 此处本不合理，但为了便于前期获取明文密钥，暂时这样处理
            if (rAtomicLong.get() < 15) {
                keyInfoRspVo.setUnkeys(jsonObject.toJSONString());
            } else {
                keyInfoRspVo.setUnkeys("已达到查看上限，此密钥不再支持查看明文！");
            }
        }
        if (keys == null) {
            throw new ServiceException("密钥转换失败！");
        }
        RMap<String, KeyInfoVo> rMap = redissonClient.getMap(KEY_CACHE + KeyTypeEnum.PEK.getType() + ":" + keyInfoVo.getClientId());
        RMap<String, KeyInfoVo> rMapHistory = redissonClient.getMap(KEY_CACHE_HISTORY  + KeyTypeEnum.PEK.getType() + ":" + keyInfoVo.getClientId());
        KeyInfoVo pKeyInfo = rMap.get(transKeyId);
        if (pKeyInfo == null) {
            pKeyInfo = rMapHistory.get(transKeyId);
        }
        // 如果有转换密钥，则将密钥使用转换密钥进行加密再返回
        if (pKeyInfo != null) {
            String pkeys = this.decryptKey(pKeyInfo.getEncryptedKey(), rootKeys, pKeyInfo.getSalt());
            JSONObject jsonObject = JSONObject.parseObject(pkeys);
            String enkeys = jsonObject.getString("publicKey");
            keys = SMHelper.sm2Encrypt(enkeys, keys);
        }
        keyInfoRspVo.setKeys(keys);
        return keyInfoRspVo;
    }

    /**
     * 加密密钥后存储
     * @param key
     * @param enKey
     * @return
     */
    private String encryptKey(String key, String enKey, String salt) {
        if (enKey == null) {
            enKey = BASE_KEY;
        }
        return SMHelper.sm4Encrypt(enKey, key) + StringUtils.reverse(salt);
    }

    /**
     * 解密密钥
     * @param encKey
     * @param enKey
     * @return
     */
    private String decryptKey(String encKey, String enKey, String salt) {
        if (enKey == null) {
            enKey = BASE_KEY;
        }
        encKey = encKey.replace(StringUtils.reverse(salt), "");
        return SMHelper.sm4Decrypt(enKey, encKey);
    }

    /**
     * 判断密钥是否过期
     * @param keyInfoVo
     * @return
     */
    private boolean isExpired(KeyInfoVo keyInfoVo) {
        if (keyInfoVo.getExpireTime() == null) {
            return false;
        }
        return keyInfoVo.getExpireTime().getTime() < System.currentTimeMillis();
    }

    /**
     * 生成随机密钥
     * @return
     */
    private String generateKey() {
        SecretKey secretKey = SecureUtil.generateKey("SM4");
        return HexUtil.encodeHexStr(secretKey.getEncoded());
    }
}
