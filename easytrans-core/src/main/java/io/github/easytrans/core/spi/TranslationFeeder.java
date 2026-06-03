package io.github.easytrans.core.spi;

import java.util.Map;
import java.util.Set;

/**
 * 用户实现此 SPI，自行决定 ID 批量转义成什么展示值。
 * {@link #getType()} 须与 Target 目标属性 {@code @TranslateField.type} 一致。
 */
public interface TranslationFeeder {

    String getType();

    Map<Object, String> batchLoad(Set<Object> ids);
}
