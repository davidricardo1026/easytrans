package io.github.easytrans.demo.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.easytrans.demo.common.Result;
import io.github.easytrans.demo.entity.po.*;
import io.github.easytrans.demo.repository.ArchiveOrderItemMapper;
import io.github.easytrans.demo.repository.ArchiveOrderMapper;
import io.github.easytrans.demo.repository.OrderItemMapper;
import io.github.easytrans.demo.repository.OrderMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 100% 干净、零侵入的 Controller。
 * 没有一个翻译相关的代码或手动调用，只管查询和返回纯净的实体 PO 对象！
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ArchiveOrderMapper archiveOrderMapper;
    private final ArchiveOrderItemMapper archiveOrderItemMapper;

    public OrderController(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                           ArchiveOrderMapper archiveOrderMapper, ArchiveOrderItemMapper archiveOrderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.archiveOrderMapper = archiveOrderMapper;
        this.archiveOrderItemMapper = archiveOrderItemMapper;
    }

    /**
     * 1. 裸 List<OrderPO> 形式，自动转义为 List<OrderPO> (就地填充 userName 和 goodsName)
     */
    @GetMapping
    public List<OrderPO> listOrders() {
        return loadOrdersWithItems();
    }

    /**
     * 2. 裸单 OrderPO 形式，自动转义为 OrderPO (就地填充)
     */
    @GetMapping("/{id}")
    public OrderPO getOrder(@PathVariable Long id) {
        OrderPO order = orderMapper.selectById(id);
        if (order != null) {
            List<OrderItemPO> items = orderItemMapper.selectList(
                    Wrappers.<OrderItemPO>query().eq("order_id", order.getId())
            );
            order.setItems(items);
            if (items != null && !items.isEmpty()) {
                order.setSingleItem(items.get(0));
                order.setItemSet(new java.util.HashSet<>(items));

                java.util.Map<String, OrderItemPO> map = new java.util.HashMap<>();
                map.put("itemKey", items.get(0));
                order.setItemMap(map);

                java.util.Map<String, List<OrderItemPO>> nestedMap = new java.util.HashMap<>();
                nestedMap.put("outerKey", items);
                order.setNestedItemMap(nestedMap);

                // 5. Deep Nested Map (Map of Map of List)
                java.util.Map<String, java.util.Map<String, List<OrderItemPO>>> deepMap = new java.util.HashMap<>();
                java.util.Map<String, List<OrderItemPO>> innerMap = new java.util.HashMap<>();
                innerMap.put("innerKey", items);
                deepMap.put("outerKey", innerMap);
                order.setDeepNestedItemMap(deepMap);
            }
        }
        return order;
    }

    /**
     * 3. 包装格式 Result<List<OrderPO>> 形式，自动转义为 Result<List<OrderPO>> (就地填充)
     */
    @GetMapping("/wrapped")
    public Result<List<OrderPO>> wrappedOrders() {
        List<OrderPO> orders = loadOrdersWithItems();
        return Result.success(orders);
    }

    /**
     * 4. 归档订单 List<ArchiveOrderPO> 形式，自动转义为 List<ArchiveOrderPO> (就地填充)
     */
    @GetMapping("/archive")
    public List<ArchiveOrderPO> listArchiveOrders() {
        List<ArchiveOrderPO> orders = archiveOrderMapper.selectList(Wrappers.emptyWrapper());
        for (ArchiveOrderPO order : orders) {
            List<ArchiveOrderItemPO> items = archiveOrderItemMapper.selectList(
                    Wrappers.<ArchiveOrderItemPO>query().eq("order_id", order.getId())
            );
            order.setItems(items);
            if (items != null && !items.isEmpty()) {
                order.setSingleItem(items.get(0));
                order.setItemSet(new java.util.HashSet<>(items));

                java.util.Map<String, ArchiveOrderItemPO> map = new java.util.HashMap<>();
                map.put("itemKey", items.get(0));
                order.setItemMap(map);

                java.util.Map<String, List<ArchiveOrderItemPO>> nestedMap = new java.util.HashMap<>();
                nestedMap.put("outerKey", items);
                order.setNestedItemMap(nestedMap);

                java.util.Map<String, java.util.Map<String, List<ArchiveOrderItemPO>>> deepMap = new java.util.HashMap<>();
                java.util.Map<String, List<ArchiveOrderItemPO>> innerMap = new java.util.HashMap<>();
                innerMap.put("innerKey", items);
                deepMap.put("outerKey", innerMap);
                order.setDeepNestedItemMap(deepMap);
            }
        }
        return orders;
    }

    /**
     * 5. 变态级多层嵌套转义演示接口
     */
    @GetMapping("/extreme")
    public ExtremeNestedPO getExtremeNested() {
        ExtremeNestedPO po = new ExtremeNestedPO();
        po.setId(999L);

        // 创建一些 OrderItemPO 数据
        OrderItemPO item1 = new OrderItemPO();
        item1.setId(10L);
        item1.setOrderId(999L);
        item1.setGoodsId(101L); // MacBook Pro

        OrderItemPO item2 = new OrderItemPO();
        item2.setId(20L);
        item2.setOrderId(999L);
        item2.setGoodsId(102L); // iPhone 15

        // Nest 1: Map of Map of List
        java.util.Map<String, List<OrderItemPO>> innerMap = new java.util.HashMap<>();
        innerMap.put("innerKey", java.util.List.of(item1, item2));
        java.util.Map<String, java.util.Map<String, List<OrderItemPO>>> outerMap = new java.util.HashMap<>();
        outerMap.put("outerKey", innerMap);
        po.setPoMapMapList(outerMap);

        // Nest 2: List of Map of Set
        java.util.Map<String, java.util.Set<OrderItemPO>> setMap = new java.util.HashMap<>();
        setMap.put("setKey", new java.util.HashSet<>(java.util.List.of(item1, item2)));
        po.setPoListMapSet(java.util.List.of(setMap));

        // Nest 3: Map with translatable Key and complex nested Map of List as Value
        java.util.Map<String, List<OrderItemPO>> valueMap = new java.util.HashMap<>();
        valueMap.put("valueKey", java.util.List.of(item1));
        java.util.Map<OrderItemPO, java.util.Map<String, List<OrderItemPO>>> transKeyMap = new java.util.HashMap<>();
        transKeyMap.put(item2, valueMap);
        po.setPoTransMapComplex(transKeyMap);

        return po;
    }

    private List<OrderPO> loadOrdersWithItems() {
        List<OrderPO> orders = orderMapper.selectList(Wrappers.emptyWrapper());
        for (OrderPO order : orders) {
            List<OrderItemPO> items = orderItemMapper.selectList(
                    Wrappers.<OrderItemPO>query().eq("order_id", order.getId())
            );
            order.setItems(items);
            if (items != null && !items.isEmpty()) {
                // 1. Single Value (Nested Object)
                order.setSingleItem(items.get(0));

                // 2. Collection (Set)
                order.setItemSet(new java.util.HashSet<>(items));

                // 3. Map
                java.util.Map<String, OrderItemPO> map = new java.util.HashMap<>();
                map.put("itemKey", items.get(0));
                order.setItemMap(map);

                java.util.Map<String, List<OrderItemPO>> nestedMap = new java.util.HashMap<>();
                nestedMap.put("outerKey", items);
                order.setNestedItemMap(nestedMap);

                // 5. Deep Nested Map (Map of Map of List)
                java.util.Map<String, java.util.Map<String, List<OrderItemPO>>> deepMap = new java.util.HashMap<>();
                java.util.Map<String, List<OrderItemPO>> innerMap = new java.util.HashMap<>();
                innerMap.put("innerKey", items);
                deepMap.put("outerKey", innerMap);
                order.setDeepNestedItemMap(deepMap);
            }
        }
        return orders;
    }
}
