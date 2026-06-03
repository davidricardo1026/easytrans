package io.github.easytrans.demo.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.easytrans.demo.common.Result;
import io.github.easytrans.demo.entity.po.ArchiveOrderItemPO;
import io.github.easytrans.demo.entity.po.ArchiveOrderPO;
import io.github.easytrans.demo.entity.po.OrderItemPO;
import io.github.easytrans.demo.entity.po.OrderPO;
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
 * 没有一个翻译相关的注解，只管查询和返回纯净的数据库 PO 对象！
 * <p>
 * 在这里，我们演示了：
 * 1. 活跃订单 (OrderPO) ➡️ 自动转义为 OrderVO 视图
 * 2. 归档订单 (ArchiveOrderPO) ➡️ 【同样】自动转义为相同的 OrderVO 视图
 * 完美的单视图模型，多数据源支持！
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
     * 1. 裸 List<OrderPO> 形式，自动转义为 List<OrderVO>
     */
    @GetMapping
    public List<OrderPO> listOrders() {
        return loadOrdersWithItems();
    }

    /**
     * 2. 裸单 OrderPO 形式，自动转义为 OrderVO
     */
    @GetMapping("/{id}")
    public OrderPO getOrder(@PathVariable Long id) {
        OrderPO order = orderMapper.selectById(id);
        if (order != null) {
            order.setItems(orderItemMapper.selectList(
                    Wrappers.<OrderItemPO>query().eq("order_id", order.getId())
            ));
        }
        return order;
    }

    /**
     * 3. 包装格式 Result<List<OrderPO>> 形式，自动转义为 Result<List<OrderVO>>
     */
    @GetMapping("/wrapped")
    public Result<List<OrderPO>> wrappedOrders() {
        List<OrderPO> orders = loadOrdersWithItems();
        return Result.success(orders);
    }

    /**
     * 4. 归档订单 List<ArchiveOrderPO> 形式，【同样且完全自动地】转义映射为 List<OrderVO>！
     */
    @GetMapping("/archive")
    public List<ArchiveOrderPO> listArchiveOrders() {
        List<ArchiveOrderPO> orders = archiveOrderMapper.selectList(Wrappers.emptyWrapper());
        for (ArchiveOrderPO order : orders) {
            List<ArchiveOrderItemPO> items = archiveOrderItemMapper.selectList(
                    Wrappers.<ArchiveOrderItemPO>query().eq("order_id", order.getId())
            );
            order.setItems(items);
        }
        return orders;
    }

    private List<OrderPO> loadOrdersWithItems() {
        List<OrderPO> orders = orderMapper.selectList(Wrappers.emptyWrapper());
        for (OrderPO order : orders) {
            List<OrderItemPO> items = orderItemMapper.selectList(
                    Wrappers.<OrderItemPO>query().eq("order_id", order.getId())
            );
            order.setItems(items);
        }
        return orders;
    }
}
