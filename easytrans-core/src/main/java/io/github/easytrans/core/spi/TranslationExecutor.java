package io.github.easytrans.core.spi;

import io.github.easytrans.core.context.TranslationContext;
import io.github.easytrans.core.mapstruct.BaseTranslationMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动转义执行器 (由 Spring 注入所有用户实现的 TranslationFeeder)
 */
public class TranslationExecutor {

    private final Map<String, TranslationFeeder> feederMap = new ConcurrentHashMap<>();

    public TranslationExecutor(List<TranslationFeeder> feeders) {
        if (feeders != null) {
            for (TranslationFeeder feeder : feeders) {
                feederMap.put(feeder.getType(), feeder);
            }
        }
    }

    /**
     * 一键自动转义的核心入口 (80-90% 场景下一行代码完成 ID 抽取、数据拉取、自动映射回填目标对象列表)
     *
     * @param sourceList 待转义的源对象列表
     * @param mapper     编译期自动生成的桥接 Mapper
     * @param <S>        Source 泛型
     * @param <T>        Target 泛型
     * @return 已完成名称转义回填的目标视图对象列表
     */
    public <S, T> List<T> translate(List<S> sourceList, BaseTranslationMapper<S, T> mapper) {
        if (sourceList == null || sourceList.isEmpty() || mapper == null) {
            return Collections.emptyList();
        }
        // 1. 创建转义运行期上下文
        TranslationContext context = new TranslationContext();
        // 2. 第一阶段：编译期硬编码生成，高吞吐地抽取所有层级嵌套的 ID
        mapper.extractAllIds(sourceList, context);
        // 3. 动态数据驱动：按需拉取所有数据源数据并灌入上下文
        execute(context);
        // 4. 第二阶段：MapStruct 编译期硬编码生成，高性能组装并回填生成完美的目标视图对象列表
        return mapper.toTargetList(sourceList, context);
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
