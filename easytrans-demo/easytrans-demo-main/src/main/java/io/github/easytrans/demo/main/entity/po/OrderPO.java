package io.github.easytrans.demo.main.entity.po;

import lombok.Data;

import java.util.List;

@Data
public class OrderPO {

    private Long id;

    private Long userId;

    private List<OrderItemPO> items;
}
