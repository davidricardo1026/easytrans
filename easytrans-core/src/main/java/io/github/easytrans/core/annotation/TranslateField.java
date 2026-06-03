package io.github.easytrans.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Target 类的属性上，声明该属性是转义属性。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TranslateField {

    /**
     * 源对象中包含 Key/ID 的属性名
     */
    String source();

    /**
     * 转义服务类型 (与 TranslationFeeder.getType() 对应)
     */
    String type();
}
