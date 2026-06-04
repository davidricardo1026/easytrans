package io.github.easytrans.core.spi;

import io.github.easytrans.core.bridge.BaseTranslationBridge;
import io.github.easytrans.core.context.TranslationContext;
import io.github.easytrans.core.registry.TranslationRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动转义执行器 (由 Spring 注入所有用户实现的 TranslationFeeder 和统一的 TranslationRegistry)
 */
public class TranslationExecutor {

    private final Map<String, TranslationFeeder> feederMap = new ConcurrentHashMap<>();
    private final TranslationRegistry translationRegistry;

    public TranslationExecutor(List<TranslationFeeder> feeders, TranslationRegistry translationRegistry) {
        this.translationRegistry = translationRegistry;
        if (feeders != null) {
            for (TranslationFeeder feeder : feeders) {
                feederMap.put(feeder.getType(), feeder);
            }
        }
    }

    /**
     * 批量就地翻译核心入口 (对传入的实体列表进行高性能就地翻译填充，完全零反射、零拷贝)
     *
     * @param sources 待翻译的实体列表
     * @param <T>     实体类型
     * @return 翻译后的实体列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> translate(List<T> sources) {
        if (sources == null || sources.isEmpty()) {
            return sources;
        }
        Class<?> clazz = sources.get(0).getClass();
        BaseTranslationBridge<T> bridge = (BaseTranslationBridge<T>) translationRegistry.findByEntityClass(clazz);
        if (bridge == null) {
            return sources;
        }
        return translate(sources, bridge);
    }

    /**
     * 单个对象就地翻译核心入口
     *
     * @param source 待翻译的实体对象
     * @param <T>    实体类型
     * @return 翻译后的实体对象
     */
    public <T> T translate(T source) {
        if (source == null) {
            return null;
        }
        translate(List.of(source));
        return source;
    }

    /**
     * 指定桥接器的底层翻译逻辑
     */
    public <T> List<T> translate(List<T> sources, BaseTranslationBridge<T> bridge) {
        if (sources == null || sources.isEmpty() || bridge == null) {
            return sources;
        }
        // 1. 创建转义运行期上下文
        TranslationContext context = new TranslationContext();
        // 2. 第一阶段：高吞吐地抽取所有层级嵌套的 ID
        bridge.extractAllIds(sources, context);
        // 3. 动态数据驱动：按需拉取所有数据源数据并灌入上下文
        execute(context);
        // 4. 第二阶段：硬编码回填属性
        bridge.writeBack(sources, context);
        return sources;
    }

    /**
     * 根据上下文收集到的所有 ID，自动加载数据并填充到上下文中
     */
    public void execute(TranslationContext context) {
        if (context == null) {
            return;
        }
        feederMap.forEach((type, feeder) -> {
            Set<Object> ids = context.getIds(type);
            if (ids != null && !ids.isEmpty()) {
                Map<Object, String> data = feeder.batchLoad(ids);
                context.initDataMap(type, data);
            }
        });
    }
}
