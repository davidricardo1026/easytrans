package io.github.easytrans.core.registry;

import io.github.easytrans.core.bridge.BaseTranslationBridge;

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

    public CompositeTranslationRegistry(List<TranslationRegistry> registries) {
        this.registries = registries != null ? registries.stream()
                .filter(r -> !(r instanceof CompositeTranslationRegistry))
                .toList() : Collections.emptyList();
        this.registriesSupplier = null;
    }

    public CompositeTranslationRegistry(Supplier<List<TranslationRegistry>> registriesSupplier) {
        this.registriesSupplier = registriesSupplier;
    }

    @Override
    public BaseTranslationBridge<?> findBridgeByEntityClass(Class<?> entityClass) {
        List<TranslationRegistry> list = getRegistries();
        for (TranslationRegistry registry : list) {
            BaseTranslationBridge<?> bridge = registry.findBridgeByEntityClass(entityClass);
            if (bridge != null) {
                return bridge;
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
