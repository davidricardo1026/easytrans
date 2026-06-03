package io.github.easytrans.demo.main.entity.po;

import lombok.Data;

@Data
public class OrderItemPO {

    private Long id;

    private Long orderId;

    private Long goodsId;
}
