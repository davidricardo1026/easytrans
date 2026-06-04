package io.github.easytrans.starter.v2.autoconfigure;

import io.github.easytrans.core.registry.CompositeTranslationRegistry;
import io.github.easytrans.core.registry.TranslationRegistry;
import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.core.spi.TranslationFeeder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;

@Configuration
public class EasyTransV2AutoConfiguration {

    @Bean
    @Primary
    public TranslationRegistry compositeTranslationRegistry(ObjectProvider<TranslationRegistry> registriesProvider) {
        // 使用 ObjectProvider.stream 延迟加载所有注册表，优雅规避 Spring Boot 2 启动时的循环引用和初始化顺序问题
        return new CompositeTranslationRegistry(() -> registriesProvider.stream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public TranslationExecutor translationExecutor(List<TranslationFeeder> feeders,
                                                   TranslationRegistry translationRegistry) {
        return new TranslationExecutor(feeders != null ? feeders : Collections.emptyList(), translationRegistry);
    }
}
