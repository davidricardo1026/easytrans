package io.github.easytrans.demo.entity.vo;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.entity.po.ArchiveOrderPO;
import io.github.easytrans.demo.entity.po.OrderPO;
import lombok.Data;

import java.util.List;

@Data
@TranslateFrom({OrderPO.class, ArchiveOrderPO.class})
public class OrderVO {

    private Long id;

    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName;

    private List<OrderItemVO> items;
}
