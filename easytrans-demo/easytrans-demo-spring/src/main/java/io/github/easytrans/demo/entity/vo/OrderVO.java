package io.github.easytrans.demo.entity.vo;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;
import io.github.easytrans.demo.entity.po.ArchiveOrderPO;
import io.github.easytrans.demo.entity.po.OrderPO;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@TranslateFrom({OrderPO.class, ArchiveOrderPO.class})
public class OrderVO {

    private Long id;

    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName;

    private List<OrderItemVO> items;

    // 1. Single Value (Nested Object)
    private OrderItemVO singleItem;

    // 2. Collection (Set)
    private Set<OrderItemVO> itemSet;

    // 3. Map
    private Map<String, OrderItemVO> itemMap;

    // 4. Map of Collection
    private Map<String, List<OrderItemVO>> nestedItemMap;

    // 5. Deep Nested Map (Map of Map of List)
    private Map<String, Map<String, List<OrderItemVO>>> deepNestedItemMap;
}
