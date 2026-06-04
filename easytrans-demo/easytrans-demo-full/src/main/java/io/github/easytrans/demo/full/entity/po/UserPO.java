package io.github.easytrans.demo.full.entity.po;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

@Data
@Translatable
public class UserPO {
    private Long id;

    @TranslateField(source = "id", type = "USER")
    private String name;
}
