package io.github.easytrans.core.bridge;

import io.github.easytrans.core.context.TranslationContext;

import java.util.List;

/**
 * 核心转义桥接器基础接口 (支持对实体对象进行零反射的就地翻译)
 *
 * @param <T> 实体类型
 */
public interface BaseTranslationBridge<T> {

    /**
     * 编译期自动生成的硬编码：极其迅速地抽取实体列表中所有嵌套层级的 ID 并加入上下文
     */
    void extractAllIds(List<T> sources, TranslationContext context);

    /**
     * 编译期自动生成的硬编码：配合上下文将翻译好的文本就地回填（Write Back）到实体的对应属性中
     */
    void writeBack(List<T> sources, TranslationContext context);
}
