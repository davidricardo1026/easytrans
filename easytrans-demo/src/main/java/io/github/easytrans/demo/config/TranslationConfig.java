package io.github.easytrans.demo.config;

import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.core.spi.TranslationFeeder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TranslationConfig {

    @Bean
    public TranslationExecutor translationExecutor(List<TranslationFeeder> feeders) {
        return new TranslationExecutor(feeders);
    }
}
