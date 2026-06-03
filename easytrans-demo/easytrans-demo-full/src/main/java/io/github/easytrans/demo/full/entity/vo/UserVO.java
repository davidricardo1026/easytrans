package io.github.easytrans.demo.full.entity.vo;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.full.entity.po.UserPO;
import lombok.Data;

@Data
@TranslateFrom(UserPO.class)
public class UserVO {
    private Long id;

    @TranslateField(source = "id", type = "USER")
    private String name;
}
