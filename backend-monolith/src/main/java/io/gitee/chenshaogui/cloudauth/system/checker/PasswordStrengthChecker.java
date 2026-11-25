package io.gitee.chenshaogui.cloudauth.system.checker;

import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码强度校验工具类，判断密码是否为简单密码
 */
@Component
@RequiredArgsConstructor
public class PasswordStrengthChecker {

    private final ISysConfigService sysConfigService;

    /**
     * 弱密码字典key
     */
    private static final String WEAK_PASSWORD_DICT_CONF_KEY = "user.password.weakPasswordDict";

    /**
     * 密码长度校验
     */
    private static final String PASSWORD_LENGTH_CONF_KEY = "user.password.passwordLength";

    /**
     * 包含连续数字或连续字母长度校验
     */
    private static final String CONTINUOUS_CHARS_LENGTH_CONF_KEY = "user.password.continuousCharsLength";

    /**
     * 包含多少种类型字符
     */
    private static final String CHAR_TYPE_COUNT_CONF_KEY = "user.password.charTypeCount";

    // 常见弱密码字典默认
    private static final Set<String> WEAK_PASSWORD_DICT = new HashSet<>(Arrays.asList(
        "123456", "12345678", "admin", "password", "root", "111111", "666666",
        "888888", "abc123", "123abc"
    ));

    // 数字正则
    private static final Pattern NUM_PATTERN = Pattern.compile("\\d");
    // 小写字母正则
    private static final Pattern LOWER_CASE_PATTERN = Pattern.compile("[a-z]");
    // 大写字母正则
    private static final Pattern UPPER_CASE_PATTERN = Pattern.compile("[A-Z]");
    // 特殊字符正则（包含标点、符号等）
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>/?~·`]");

    /**
     * 检查密码是否为简单密码
     * @param password 待校验的密码
     * @return 校验结果：true=简单密码，false=复杂密码
     */
    public boolean isSimplePassword(String password) {
        // 规则1：密码为空或长度小于6位
        int passwordLength = 6;
        String passwordLengthStr = sysConfigService.selectConfigByKey(PASSWORD_LENGTH_CONF_KEY);
        if(StringUtils.isNotEmpty(passwordLengthStr)){
            passwordLength = Integer.parseInt(passwordLengthStr);
        }
        if (password == null || password.length() < passwordLength) {
            return true;
        }

        // 规则2：匹配常见弱密码字典
        String weakPasswordDictStr = sysConfigService.selectConfigByKey(WEAK_PASSWORD_DICT_CONF_KEY);
        if(StringUtils.isNotBlank(weakPasswordDictStr)){
            WEAK_PASSWORD_DICT.clear();
            String[] wpArr = weakPasswordDictStr.split(",");
            WEAK_PASSWORD_DICT.addAll( new HashSet<>(Arrays.asList(wpArr)));
        }
        if (WEAK_PASSWORD_DICT.contains(password.toLowerCase())) {
            return true;
        }

        // 规则3：仅包含单一字符类型（数字/小写字母/大写字母/特殊字符）
        int charTypeCountV = 6;
        String charTypeCountVStr = sysConfigService.selectConfigByKey(CHAR_TYPE_COUNT_CONF_KEY);
        if(StringUtils.isNotEmpty(charTypeCountVStr)){
            charTypeCountV = Integer.parseInt(charTypeCountVStr);
        }
        boolean hasNum = NUM_PATTERN.matcher(password).find();
        boolean hasLower = LOWER_CASE_PATTERN.matcher(password).find();
        boolean hasUpper = UPPER_CASE_PATTERN.matcher(password).find();
        boolean hasSpecial = SPECIAL_CHAR_PATTERN.matcher(password).find();
        int charTypeCount = 0;
        if (hasNum) charTypeCount++;
        if (hasLower || hasUpper) charTypeCount++; // 大小写字母视为同一类（可分开）
        if (hasSpecial) charTypeCount++;
        if (charTypeCount < charTypeCountV) { // 少于2种字符类型则为简单密码
            return true;
        }

        // 规则4：包含连续数字或连续字母（长度≥5）
        int continuousCharsLength = 5;
        String continuousCharsStr = sysConfigService.selectConfigByKey(CONTINUOUS_CHARS_LENGTH_CONF_KEY);
        if(StringUtils.isNotEmpty(continuousCharsStr)){
            continuousCharsLength = Integer.parseInt(continuousCharsStr);
        }
        if (hasContinuousChars(password, continuousCharsLength)) {
            return true;
        }

        // 所有规则均不满足，为复杂密码
        return false;
    }

    /**
     * 检查密码是否包含连续的数字或字母（如12345、abcde）
     * @param password 密码
     * @param length 连续字符的最小长度
     * @return true=包含连续字符，false=不包含
     */
    private boolean hasContinuousChars(String password, int length) {
        // 检查连续数字
        if (hasContinuousDigits(password, length)) {
            return true;
        }
        // 检查连续字母（忽略大小写）
        if (hasContinuousLetters(password, length)) {
            return true;
        }
        return false;
    }

    /**
     * 检查连续数字（如12345、98765）
     */
    private boolean hasContinuousDigits(String password, int length) {
        char[] chars = password.toCharArray();
        for (int i = 0; i <= chars.length - length; i++) {
            boolean isIncreasing = true;
            boolean isDecreasing = true;
            for (int j = 0; j < length - 1; j++) {
                if (!Character.isDigit(chars[i + j]) || !Character.isDigit(chars[i + j + 1])) {
                    isIncreasing = false;
                    isDecreasing = false;
                    break;
                }
                int current = chars[i + j] - '0';
                int next = chars[i + j + 1] - '0';
                if (next != current + 1) {
                    isIncreasing = false;
                }
                if (next != current - 1) {
                    isDecreasing = false;
                }
            }
            if (isIncreasing || isDecreasing) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查连续字母（如abcde、EDCBA）
     */
    private boolean hasContinuousLetters(String password, int length) {
        char[] chars = password.toLowerCase().toCharArray(); // 统一转为小写
        for (int i = 0; i <= chars.length - length; i++) {
            boolean isIncreasing = true;
            boolean isDecreasing = true;
            for (int j = 0; j < length - 1; j++) {
                if (!Character.isLetter(chars[i + j]) || !Character.isLetter(chars[i + j + 1])) {
                    isIncreasing = false;
                    isDecreasing = false;
                    break;
                }
                int current = chars[i + j] - 'a';
                int next = chars[i + j + 1] - 'a';
                if (next != current + 1) {
                    isIncreasing = false;
                }
                if (next != current - 1) {
                    isDecreasing = false;
                }
            }
            if (isIncreasing || isDecreasing) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取密码简单的原因（用于提示用户）
     * @param password 密码
     * @return 原因描述
     */
    public String getSimpleReason(String password) {
        int passwordLength = 6;
        String passwordLengthStr = sysConfigService.selectConfigByKey(PASSWORD_LENGTH_CONF_KEY);
        if(StringUtils.isNotEmpty(passwordLengthStr)){
            passwordLength = Integer.parseInt(passwordLengthStr);
        }
        if (password == null || password.length() < passwordLength) {
            return "密码长度不能小于"+passwordLength+"位";
        }

        String weakPasswordDictStr = sysConfigService.selectConfigByKey(WEAK_PASSWORD_DICT_CONF_KEY);
        if(StringUtils.isNotBlank(weakPasswordDictStr)){
            WEAK_PASSWORD_DICT.clear();
            String[] wpArr = weakPasswordDictStr.split(",");
            WEAK_PASSWORD_DICT.addAll( new HashSet<>(Arrays.asList(wpArr)));
        }
        if (WEAK_PASSWORD_DICT.contains(password.toLowerCase())) {
            return "密码为常见弱密码，建议更换";
        }

        int charTypeCountV = 6;
        String charTypeCountVStr = sysConfigService.selectConfigByKey(CHAR_TYPE_COUNT_CONF_KEY);
        if(StringUtils.isNotEmpty(charTypeCountVStr)){
            charTypeCountV = Integer.parseInt(charTypeCountVStr);
        }
        boolean hasNum = NUM_PATTERN.matcher(password).find();
        boolean hasLower = LOWER_CASE_PATTERN.matcher(password).find();
        boolean hasUpper = UPPER_CASE_PATTERN.matcher(password).find();
        boolean hasSpecial = SPECIAL_CHAR_PATTERN.matcher(password).find();
        int charTypeCount = 0;
        if (hasNum) charTypeCount++;
        if (hasLower || hasUpper) charTypeCount++;
        if (hasSpecial) charTypeCount++;
        if (charTypeCount < charTypeCountV) {
            return "密码需包含至少"+charTypeCountV+"种字符类型（数字、字母、特殊符号）";
        }

        int continuousCharsLength = 5;
        String continuousCharsStr = sysConfigService.selectConfigByKey(CONTINUOUS_CHARS_LENGTH_CONF_KEY);
        if(StringUtils.isNotEmpty(continuousCharsStr)){
            continuousCharsLength = Integer.parseInt(continuousCharsStr);
        }
        if (hasContinuousChars(password, continuousCharsLength)) {
            return "密码包含连续"+continuousCharsLength+"个数字或字母，建议修改";
        }
        return "密码格式合法";
    }
}
