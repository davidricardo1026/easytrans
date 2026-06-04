package io.github.easytrans.demo.main.entity.po;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.util.List;

@Data
@Translatable
public class OrderPO {

    private Long id;

    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName;

    private List<OrderItemPO> items;
}
