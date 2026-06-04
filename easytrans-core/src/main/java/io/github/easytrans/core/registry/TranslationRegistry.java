package io.github.easytrans.core.registry;

import io.github.easytrans.core.bridge.BaseTranslationBridge;

/**
 * 转义注册表：用于根据实体类型寻找对应的 BaseTranslationBridge
 */
public interface TranslationRegistry {

    @SuppressWarnings("unchecked")
    default <T> BaseTranslationBridge<T> findByEntityClass(Class<T> entityClass) {
        return (BaseTranslationBridge<T>) findBridgeByEntityClass(entityClass);
    }

    BaseTranslationBridge<?> findBridgeByEntityClass(Class<?> entityClass);
}
