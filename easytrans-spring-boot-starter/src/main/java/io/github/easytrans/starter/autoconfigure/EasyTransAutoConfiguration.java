package io.github.easytrans.starter.autoconfigure;

import io.github.easytrans.core.registry.CompositeTranslationRegistry;
import io.github.easytrans.core.registry.TranslationRegistry;
import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.core.spi.TranslationFeeder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;

@AutoConfiguration
public class EasyTransAutoConfiguration {

    @Bean
    @Primary
    public TranslationRegistry compositeTranslationRegistry(List<TranslationRegistry> registries) {
        return new CompositeTranslationRegistry(registries != null ? registries : Collections.emptyList());
    }

    @Bean
    @ConditionalOnMissingBean
    public TranslationExecutor translationExecutor(List<TranslationFeeder> feeders) {
        return new TranslationExecutor(feeders != null ? feeders : Collections.emptyList());
    }
}
