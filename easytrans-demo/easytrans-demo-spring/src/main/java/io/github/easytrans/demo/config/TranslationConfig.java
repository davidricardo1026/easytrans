package io.github.easytrans.demo.config;

import io.github.easytrans.core.registry.TranslationRegistry;
import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.core.spi.TranslationFeeder;
import io.github.easytrans.demo.entity.vo.generated.GeneratedTranslationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TranslationConfig {

    @Bean
    public TranslationRegistry translationRegistry() {
        return new GeneratedTranslationRegistry();
    }

    @Bean
    public TranslationExecutor translationExecutor(List<TranslationFeeder> feeders) {
        return new TranslationExecutor(feeders);
    }
}
