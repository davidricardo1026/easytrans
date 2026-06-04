package io.github.easytrans.demo.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@TableName("t_order")
@Translatable
public class OrderPO {

    @TableId
    private Long id;

    private Long userId;

    @TableField(exist = false)
    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName;

    @TableField(exist = false)
    private List<OrderItemPO> items;

    // 1. Single Value (Nested Object)
    @TableField(exist = false)
    private OrderItemPO singleItem;

    // 2. Collection (Set)
    @TableField(exist = false)
    private Set<OrderItemPO> itemSet;

    // 3. Map
    @TableField(exist = false)
    private Map<String, OrderItemPO> itemMap;

    // 4. Map of Collection
    @TableField(exist = false)
    private Map<String, List<OrderItemPO>> nestedItemMap;

    // 5. Deep Nested Map (Map of Map of List)
    @TableField(exist = false)
    private Map<String, Map<String, List<OrderItemPO>>> deepNestedItemMap;
}
