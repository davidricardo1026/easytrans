package io.github.easytrans.demo.main;

import io.github.easytrans.core.mapstruct.BaseTranslationMapper;
import io.github.easytrans.core.registry.TranslationRegistry;
import io.github.easytrans.core.spi.TranslationExecutor;
import io.github.easytrans.core.spi.TranslationFeeder;
import io.github.easytrans.demo.main.entity.po.OrderItemPO;
import io.github.easytrans.demo.main.entity.po.OrderPO;
import io.github.easytrans.demo.main.entity.vo.OrderVO;
import io.github.easytrans.demo.main.entity.vo.generated.GeneratedTranslationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 非 Spring 环境下的原生 Java Main 运行示例。
 * 完美的演示了 EasyTrans 架构由于编译期生成、零反射的设计，使得在普通 Java 应用程序中同样能开箱即用、轻松运行。
 */
public class EasyTransMainApplication {

    public static void main(String[] args) {
        System.out.println("=== 开始运行: EasyTrans 非 Spring 环境关联转义 DEMO ===");

        // 1. 初始化模拟的 PO 数据库原始数据
        List<OrderPO> orderPOList = createMockOrders();
        System.out.println("【原始 PO 数据】: " + orderPOList);

        // 2. 手动创建非 Spring 模式下的 TranslationRegistry
        // 由于 easytrans-main 编译期指定了 easytrans.enable.spring=false，
        // GeneratedTranslationRegistry 内部在实例化时会直接 new 其所需的所有 bridge 转换器，无需任何依赖注入！
        TranslationRegistry translationRegistry = new GeneratedTranslationRegistry();

        // 3. 手动注册所需业务 of TranslationFeeder (非 Spring 注册)
        List<TranslationFeeder> feeders = new ArrayList<>();
        feeders.add(new SimpleUserFeeder());
        feeders.add(new SimpleGoodsFeeder());

        // 4. 手动实例化转义执行器
        TranslationExecutor translationExecutor = new TranslationExecutor(feeders);

        // 5. 获取 OrderPO 对应的映射转换器 Bridge 实例
        BaseTranslationMapper<Object, Object> mapper = translationRegistry.findBySourceClass(OrderPO.class);
        if (mapper == null) {
            System.err.println("未找到对应的 TranslationMapper，请检查 VO 是否使用了 @TranslateFrom(OrderPO.class)");
            return;
        }

        // 6. 执行核心转义链路！ (包含 ID 递归收集、Feeder 批量加载、MapStruct 高速回填转换)
        @SuppressWarnings("unchecked")
        List<OrderVO> orderVOList = (List<OrderVO>) (List<?>) translationExecutor.translate((List<Object>) (List<?>) orderPOList,
                                                                                            mapper);

        // 7. 输出转义后的结果
        System.out.println("\n【转义成功的 VO 数据列表】:");
        for (OrderVO vo : orderVOList) {
            System.out.println("订单 ID: " + vo.getId() + ", 用户 ID: " + vo.getUserId() + " -> 用户姓名: " + vo.getUserName());
            if (vo.getItems() != null) {
                for (var item : vo.getItems()) {
                    System.out.println("  └─ 子项 ID: " + item.getId() + ", 商品 ID: " + item.getGoodsId() + " -> 商品名称: " + item.getGoodsName());
                }
            }
        }

        System.out.println("\n=== 运行结束: 物理性能极限，零反射极速完成关联 ID 转义！ ===");
    }

    private static List<OrderPO> createMockOrders() {
        List<OrderPO> list = new ArrayList<>();

        // 订单 1: 用户 1 买 MacBook 和 iPhone
        OrderPO order1 = new OrderPO();
        order1.setId(1L);
        order1.setUserId(1L);

        List<OrderItemPO> items1 = new ArrayList<>();
        OrderItemPO item1_1 = new OrderItemPO();
        item1_1.setId(1001L);
        item1_1.setGoodsId(101L);
        items1.add(item1_1);

        OrderItemPO item1_2 = new OrderItemPO();
        item1_2.setId(1002L);
        item1_2.setGoodsId(102L);
        items1.add(item1_2);

        order1.setItems(items1);
        list.add(order1);

        // 订单 2: 用户 2 买 AirPods
        OrderPO order2 = new OrderPO();
        order2.setId(2L);
        order2.setUserId(2L);

        List<OrderItemPO> items2 = new ArrayList<>();
        OrderItemPO item2_1 = new OrderItemPO();
        item2_1.setId(1003L);
        item2_1.setGoodsId(103L);
        items2.add(item2_1);

        order2.setItems(items2);
        list.add(order2);

        return list;
    }

    /**
     * 模拟用户服务 Feeder (非 Spring 托管)
     */
    private static class SimpleUserFeeder implements TranslationFeeder {
        @Override
        public String getType() {
            return "USER_SERVICE";
        }

        @Override
        public Map<Object, String> batchLoad(Set<Object> ids) {
            System.out.println("  >> [USER_SERVICE] 收到批量拉取 ID 列表: " + ids);
            // 模拟极速批量查库或微服务调用
            return Map.of(
                    1L, "张三(Main模拟)",
                    2L, "李四(Main模拟)"
            );
        }
    }

    /**
     * 模拟商品服务 Feeder (非 Spring 托管)
     */
    private static class SimpleGoodsFeeder implements TranslationFeeder {
        @Override
        public String getType() {
            return "GOODS_SERVICE";
        }

        @Override
        public Map<Object, String> batchLoad(Set<Object> ids) {
            System.out.println("  >> [GOODS_SERVICE] 收到批量拉取 ID 列表: " + ids);
            // 模拟极速批量查库或微服务调用
            return Map.of(
                    101L, "MacBook Pro M3",
                    102L, "iPhone 15 Pro",
                    103L, "AirPods Pro"
            );
        }
    }
}
