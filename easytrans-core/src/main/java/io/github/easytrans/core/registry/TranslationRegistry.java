package io.github.easytrans.core.registry;

import io.github.easytrans.core.mapstruct.BaseTranslationMapper;

/**
 * 转义注册表：用于根据输入的 Source 类型，寻找对应的 BaseTranslationMapper
 */
public interface TranslationRegistry {

    @SuppressWarnings("unchecked")
    default BaseTranslationMapper<Object, Object> findBySourceClass(Class<?> sourceClass) {
        return (BaseTranslationMapper<Object, Object>) findMapperBySourceClass(sourceClass);
    }

    BaseTranslationMapper<?, ?> findMapperBySourceClass(Class<?> sourceClass);
}
