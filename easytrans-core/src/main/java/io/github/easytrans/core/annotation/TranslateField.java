package io.github.easytrans.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在可翻译实体的属性上，声明该属性是一个需要就地回填的转义属性。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TranslateField {

    /**
     * 当前实体中包含关联 ID 的属性名（例如通过 goodsId 翻译到当前 goodsName）
     */
    String source();

    /**
     * 转义服务类型 (与 TranslationFeeder.getType() 对应，例如 GOODS_SERVICE)
     */
    String type();
}
