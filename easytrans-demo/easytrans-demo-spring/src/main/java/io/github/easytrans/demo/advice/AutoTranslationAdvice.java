package io.github.easytrans.demo.advice;

import io.github.easytrans.core.bridge.BaseTranslationBridge;
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
 * 满足 100% 零侵入的就地翻译 ResponseBodyAdvice。
 * 在 HTTP 响应前自动提取返回值，检查是否是标注了 @Translatable 的实体。
 * 如果是，则在内存中高吞吐完成就地翻译填充，并原样返回实体对象。
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
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return null;
        }

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

        Class<?> entityClass;
        List<Object> sourceList;

        if (rawData instanceof List list) {
            if (list.isEmpty()) {
                return body;
            }
            sourceList = (List<Object>) list;
            entityClass = sourceList.get(0).getClass();
        } else {
            sourceList = Collections.singletonList(rawData);
            entityClass = rawData.getClass();
        }

        BaseTranslationBridge<?> bridge = translationRegistry.findByEntityClass(entityClass);
        if (bridge == null) {
            return body;
        }

        // 统一在底层完成高性能、零反射、100% 纯内存就地翻译！
        translationExecutor.translate(sourceList, (BaseTranslationBridge<Object>) bridge);

        return body;
    }
}
