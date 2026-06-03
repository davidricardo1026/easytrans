package io.github.easytrans.core.context;

import java.util.*;

/**
 * 转义上下文：在第一阶段用于硬编码递归收集所有 ID，第二阶段用于批量加载后高速检索转义名称
 */
public class TranslationContext {

    private final Map<String, Set<Object>> idRegistry = new HashMap<>();
    private final Map<String, Map<Object, String>> dataRegistry = new HashMap<>();

    public void collectId(String type, Object id) {
        if (id != null) {
            idRegistry.computeIfAbsent(type, k -> new HashSet<>()).add(id);
        }
    }

    public Set<Object> getIds(String type) {
        return idRegistry.getOrDefault(type, Collections.emptySet());
    }

    public void initDataMap(String type, Map<Object, String> map) {
        if (map != null) {
            dataRegistry.put(type, map);
        }
    }

    public String getName(String type, Object id) {
        if (id == null) {
            return null;
        }
        return dataRegistry.getOrDefault(type, Collections.emptyMap()).getOrDefault(id, "未知");
    }
}
