package io.github.easytrans.demo.advice;

import io.github.easytrans.core.mapstruct.BaseTranslationMapper;
import io.github.easytrans.core.registry.TranslationRegistry;
import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.demo.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collections;
import java.util.List;

/**
 * 满足 80-90% 企业开发中常见场景的自动转义 ResponseBodyAdvice (100% 零侵入，用户无需编写任何注解！)
 * 支持：
 * 1. 裸 List<PO> 返回
 * 2. 裸单 PO 返回
 * 3. 统一包装格式 Result<List<PO>> 或 Result<PO> 返回
 */
@ControllerAdvice
@SuppressWarnings({"rawtypes", "unchecked"})
public class AutoTranslationAdvice implements ResponseBodyAdvice<Object> {

    private final TranslationRegistry translationRegistry;
    private final TranslationExecutor translationExecutor;

    public AutoTranslationAdvice(TranslationRegistry translationRegistry, TranslationExecutor translationExecutor) {
        this.translationRegistry = translationRegistry;
        this.translationExecutor = translationExecutor;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 全局拦截，但在 beforeBodyWrite 进行极低延迟的精准过滤
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return null;
        }

        // 1. 解包：定位到真实的 PO 数据源 (裸单 PO, 裸 List, 还是包装在 Result 里的数据)
        Object rawData = body;
        boolean isResultWrapper = false;
        Result resultWrapper = null;

        if (body instanceof Result r) {
            rawData = r.getData();
            isResultWrapper = true;
            resultWrapper = r;
        }

        if (rawData == null) {
            return body;
        }

        // 2. 统一获取 Source 类型，用于精准推断匹配的翻译器
        Class<?> sourceClass;
        List<Object> sourceList;
        boolean isSingleElement = false;

        if (rawData instanceof List list) {
            if (list.isEmpty()) {
                return body;
            }
            sourceList = (List<Object>) list;
            sourceClass = sourceList.get(0).getClass();
        } else {
            sourceList = Collections.singletonList(rawData);
            sourceClass = rawData.getClass();
            isSingleElement = true;
        }

        // 3. 根据 Source 类精准匹配是否有编译生成的硬编码翻译器 (超轻量 Map 寻址，如果非 Source 直接秒回，极致性能无副作用)
        BaseTranslationMapper<Object, Object> mapper = translationRegistry.findBySourceClass(sourceClass);
        if (mapper == null) {
            return body;
        }

        // 4. 一键完成：ID 收集、Feeder 并行/串行加载数据、MapStruct 编译期硬编码回填 VO，三合一一站式服务！
        List<Object> targetList = translationExecutor.translate(sourceList, mapper);

        // 5. 装包并返回符合原接口定义的结构 (自动从 Source 升级成完美的 Target 视图对象)
        Object finalData = isSingleElement ? targetList.get(0) : targetList;

        if (isResultWrapper) {
            resultWrapper.setData(finalData);
            return resultWrapper;
        }

        return finalData;
    }
}
