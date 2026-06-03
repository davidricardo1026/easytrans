package io.github.easytrans.core.registry;

import io.github.easytrans.core.mapstruct.BaseTranslationMapper;

import java.util.Collections;
import java.util.List;

/**
 * 组合转义注册表：用于聚合多个模块生成的 TranslationRegistry，在多模块场景下提供统一检索。
 */
public class CompositeTranslationRegistry implements TranslationRegistry {

    private final List<TranslationRegistry> registries;

    public CompositeTranslationRegistry(List<TranslationRegistry> registries) {
        // 过滤掉任何 CompositeTranslationRegistry 实例，防止自循环注入导致的无限递归（栈溢出）
        this.registries = registries != null ? registries.stream()
                .filter(r -> !(r instanceof CompositeTranslationRegistry))
                .toList() : Collections.emptyList();
    }

    @Override
    public BaseTranslationMapper<?, ?> findMapperBySourceClass(Class<?> sourceClass) {
        for (TranslationRegistry registry : registries) {
            BaseTranslationMapper<?, ?> mapper = registry.findMapperBySourceClass(sourceClass);
            if (mapper != null) {
                return mapper;
            }
        }
        return null;
    }
}
