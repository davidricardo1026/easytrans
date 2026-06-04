package io.github.easytrans.demo.main.entity.po;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

@Data
@Translatable
public class OrderItemPO {

    private Long id;

    private Long orderId;

    private Long goodsId;

    @TranslateField(source = "goodsId", type = "GOODS_SERVICE")
    private String goodsName;
}
