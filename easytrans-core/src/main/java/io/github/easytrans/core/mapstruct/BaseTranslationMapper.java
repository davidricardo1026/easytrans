package io.github.easytrans.core.mapstruct;

import io.github.easytrans.core.context.TranslationContext;
import org.mapstruct.Context;

import java.util.List;

/**
 * 核心转义转换器基础接口 (支持从源对象 S 到 目标对象 T 的极速转义)
 *
 * @param <S> Source 源类型 (输入对象，如 Entity)
 * @param <T> Target 目标类型 (输出对象，如 VO, DTO)
 */
public interface BaseTranslationMapper<S, T> {

    /**
     * 编译期自动生成的硬编码：极其迅速地抽取所有嵌套层级的 ID
     */
    void extractAllIds(List<S> sources, @Context TranslationContext context);

    /**
     * 编译期自动生成的硬编码：MapStruct 配合上下文极速组装生成目标视图对象列表
     */
    List<T> toTargetList(List<S> sources, @Context TranslationContext context);
}
