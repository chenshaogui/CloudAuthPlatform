package io.gitee.chenshaogui.cloudauth.casm.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {

    //密钥模式，默认为AES
    String mode() default "SM4";

    //接口鉴权
    String permission() default "NONE";
}
