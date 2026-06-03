package io.github.easytrans.core.registry;

import io.github.easytrans.core.mapstruct.BaseTranslationMapper;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 组合转义注册表：用于聚合多个模块生成的 TranslationRegistry，在多模块场景下提供统一检索。
 * 支持传入 Supplier 延迟加载，从而在 Spring 容器初始化期间 100% 避免因循环依赖（BeanCurrentlyInCreationException）导致的启动失败。
 */
public class CompositeTranslationRegistry implements TranslationRegistry {

    private final Supplier<List<TranslationRegistry>> registriesSupplier;
    private volatile List<TranslationRegistry> registries;

    /**
     * 原生 Java / 非 Spring 环境构造器（支持急切加载）
     * 适用于没有 IoC 容器、需要手动装配的场景。
     * 直接传入已经实例化好的注册表列表，无需延迟加载，简单直观。
     */
    public CompositeTranslationRegistry(List<TranslationRegistry> registries) {
        this.registries = registries != null ? registries.stream()
                .filter(r -> !(r instanceof CompositeTranslationRegistry))
                .toList() : Collections.emptyList();
        this.registriesSupplier = null;
    }

    /**
     * Spring 自动配置专用构造器（支持延迟加载）
     * 适用于 Spring Boot 环境。通过传入 Supplier 延迟在运行期首次翻译时才拉取容器中的所有 Registry，
     * 优雅地规避了 Spring 容器在初始化 Bean 期间因类型匹配导致的循环依赖（BeanCurrentlyInCreationException）。
     */
    public CompositeTranslationRegistry(Supplier<List<TranslationRegistry>> registriesSupplier) {
        this.registriesSupplier = registriesSupplier;
    }

    @Override
    public BaseTranslationMapper<?, ?> findMapperBySourceClass(Class<?> sourceClass) {
        List<TranslationRegistry> list = getRegistries();
        for (TranslationRegistry registry : list) {
            BaseTranslationMapper<?, ?> mapper = registry.findMapperBySourceClass(sourceClass);
            if (mapper != null) {
                return mapper;
            }
        }
        return null;
    }

    private List<TranslationRegistry> getRegistries() {
        if (registriesSupplier == null) {
            return registries;
        }
        if (registries == null) {
            synchronized (this) {
                if (registries == null) {
                    List<TranslationRegistry> raw = registriesSupplier.get();
                    registries = raw != null ? raw.stream()
                            .filter(r -> !(r instanceof CompositeTranslationRegistry))
                            .toList() : Collections.emptyList();
                }
            }
        }
        return registries;
    }
}
