package io.github.easytrans.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Target (目标/展示) 类上，声明其支持从哪些 Source (源) 类转义映射而来。
 * 支持传入多个 Source 类，编译期会自动为每个 Source ➡️ Target 映射生成极速的 MapStruct 转换器。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TranslateFrom {

    /**
     * 源对象类数组
     */
    Class<?>[] value();
}
