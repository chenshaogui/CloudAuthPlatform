package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * JSON 工具类
 *
 * @author 芋道源码
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        // 1. 核心：设置统一的日期格式（匹配 JSON 中的 "2025-11-25 13:48:54"）
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 必须指定时区（东八区），避免解析后日期偏移
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        OBJECT_MAPPER.setDateFormat(dateFormat);

        // 2. 禁用日期转时间戳（确保序列化时也输出字符串格式，而非数字）
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. 可选：忽略未知字段（避免其他字段不匹配导致的额外报错）
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 将对象转换为指定类型（直接转换，避免重复 toJsonString + parseObject 的双重序列化）
     *
     * @param fromValue 源对象（通常是 Map、List 等 Jackson 可识别的中间结构）
     * @param toValueType 目标类型
     * @param <T> 泛型
     * @return 转换后的对象
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (ObjectUtil.isNull(fromValue)) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    /**
     * 将对象转换为复杂 JavaType（如 List&lt;Bean&gt;）
     */
    public static <T> T convertValue(Object fromValue, com.fasterxml.jackson.databind.JavaType toValueType) {
        if (ObjectUtil.isNull(fromValue)) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    /**
     * 将对象转换为JSON格式的字符串
     *
     * @param object 要转换的对象
     * @return JSON格式的字符串，如果对象为null，则返回null
     * @throws RuntimeException 如果转换过程中发生JSON处理异常，则抛出运行时异常
     */
    public static String toJsonString(Object object) {
        if (ObjectUtil.isNull(object)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON格式的字符串转换为指定类型的对象
     *
     * @param text  JSON格式的字符串
     * @param clazz 要转换的目标对象类型
     * @param <T>   目标对象的泛型类型
     * @return 转换后的对象，如果字符串为空则返回null
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static <T> T parseObject(String text, Class<T> clazz) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将字节数组转换为指定类型的对象
     *
     * @param bytes 字节数组
     * @param clazz 要转换的目标对象类型
     * @param <T>   目标对象的泛型类型
     * @return 转换后的对象，如果字节数组为空则返回null
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (ArrayUtil.isEmpty(bytes)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON格式的字符串转换为指定类型的对象，支持复杂类型
     *
     * @param text          JSON格式的字符串
     * @param typeReference 指定类型的TypeReference对象
     * @param <T>           目标对象的泛型类型
     * @return 转换后的对象，如果字符串为空则返回null
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static <T> T parseObject(String text, TypeReference<T> typeReference) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON格式的字符串转换为Dict对象
     *
     * @param text JSON格式的字符串
     * @return 转换后的Dict对象，如果字符串为空或者不是JSON格式则返回null
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static Dict parseMap(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructType(Dict.class));
        } catch (MismatchedInputException e) {
            // 类型不匹配说明不是json
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON格式的字符串转换为Dict对象的列表
     *
     * @param text JSON格式的字符串
     * @return 转换后的Dict对象的列表，如果字符串为空则返回null
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static List<Dict> parseArrayMap(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Dict.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON格式的字符串转换为指定类型对象的列表
     *
     * @param text  JSON格式的字符串
     * @param clazz 要转换的目标对象类型
     * @param <T>   目标对象的泛型类型
     * @return 转换后的对象的列表，如果字符串为空则返回空列表
     * @throws RuntimeException 如果转换过程中发生IO异常，则抛出运行时异常
     */
    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        if (StrUtil.isBlank(text)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 判断字符串是否为合法 JSON（对象或数组）
     *
     * @param str 待校验字符串
     * @return true = 合法 JSON，false = 非法或空
     */
    public static boolean isJson(String str) {
        if (StrUtil.isBlank(str)) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为 JSON 对象（{}）
     *
     * @param str 待校验字符串
     * @return true = JSON 对象
     */
    public static boolean isJsonObject(String str) {
        if (StrUtil.isBlank(str)) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(str);
            return node.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为 JSON 数组（[]）
     *
     * @param str 待校验字符串
     * @return true = JSON 数组
     */
    public static boolean isJsonArray(String str) {
        if (StrUtil.isBlank(str)) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(str);
            return node.isArray();
        } catch (Exception e) {
            return false;
        }
    }


}