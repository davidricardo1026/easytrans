package io.github.easytrans.demo.main.entity.vo;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.main.entity.po.OrderPO;
import lombok.Data;

import java.util.List;

@Data
@TranslateFrom({OrderPO.class})
public class OrderVO {

    private Long id;

    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName;

    private List<OrderItemVO> items;
}
