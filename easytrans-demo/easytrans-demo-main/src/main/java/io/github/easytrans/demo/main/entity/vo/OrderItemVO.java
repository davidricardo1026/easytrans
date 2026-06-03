package io.github.easytrans.demo.main.entity.vo;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.main.entity.po.OrderItemPO;
import lombok.Data;

@Data
@TranslateFrom({OrderItemPO.class})
public class OrderItemVO {

    private Long id;

    private Long goodsId;

    @TranslateField(source = "goodsId", type = "GOODS_SERVICE")
    private String goodsName;
}
