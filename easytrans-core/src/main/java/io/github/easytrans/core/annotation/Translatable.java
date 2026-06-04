package io.github.easytrans.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在实体（如 Domain 实体, PO, DTO, VO）类上，声明该类是一个可进行就地翻译的对象。
 * 编译期 APT 将自动为该类生成硬编码的翻译 Bridge。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Translatable {
}
