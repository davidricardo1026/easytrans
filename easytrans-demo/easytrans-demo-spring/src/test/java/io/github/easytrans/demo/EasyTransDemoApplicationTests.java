package io.github.easytrans.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class EasyTransDemoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试 1：裸 List<OrderPO> 形式，自动就地转义填充
     * - 校验订单列表大小、订单主体信息翻译（userId -> userName）
     * - 校验订单项嵌套列表的翻译（goodsId -> goodsName）
     */
    @Test
    public void testListOrders() throws Exception {
        mockMvc.perform(get("/orders")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 校验列表长度
                .andExpect(jsonPath("$", hasSize(3)))
                // 订单 1 校验：用户 1 -> 张三
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].userId", is(1)))
                .andExpect(jsonPath("$[0].userName", is("张三")))
                .andExpect(jsonPath("$[0].items", hasSize(2)))
                .andExpect(jsonPath("$[0].items[0].id", is(1)))
                .andExpect(jsonPath("$[0].items[0].goodsId", is(101)))
                .andExpect(jsonPath("$[0].items[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$[0].items[1].id", is(2)))
                .andExpect(jsonPath("$[0].items[1].goodsId", is(102)))
                .andExpect(jsonPath("$[0].items[1].goodsName", is("iPhone 15")))
                // 订单 2 校验：用户 2 -> 李四
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].userId", is(2)))
                .andExpect(jsonPath("$[1].userName", is("李四")))
                .andExpect(jsonPath("$[1].items", hasSize(1)))
                .andExpect(jsonPath("$[1].items[0].id", is(3)))
                .andExpect(jsonPath("$[1].items[0].goodsId", is(103)))
                .andExpect(jsonPath("$[1].items[0].goodsName", is("AirPods Pro")))
                // 订单 3 校验：用户 1 -> 张三
                .andExpect(jsonPath("$[2].id", is(3)))
                .andExpect(jsonPath("$[2].userId", is(1)))
                .andExpect(jsonPath("$[2].userName", is("张三")))
                .andExpect(jsonPath("$[2].items", hasSize(2)))
                .andExpect(jsonPath("$[2].items[0].goodsId", is(101)))
                .andExpect(jsonPath("$[2].items[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$[2].items[1].goodsId", is(103)))
                .andExpect(jsonPath("$[2].items[1].goodsName", is("AirPods Pro")));
    }

    /**
     * 测试 2：裸单 OrderPO 形式，自动就地转义填充
     * - 校验单个对象的翻译（userId -> userName）以及子项的翻译
     */
    @Test
    public void testGetOrderSingle() throws Exception {
        mockMvc.perform(get("/orders/1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.userName", is("张三")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].goodsId", is(101)))
                .andExpect(jsonPath("$.items[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$.items[1].goodsId", is(102)))
                .andExpect(jsonPath("$.items[1].goodsName", is("iPhone 15")))

                // 1. Single Value (Nested Object)
                .andExpect(jsonPath("$.singleItem.goodsId", is(101)))
                .andExpect(jsonPath("$.singleItem.goodsName", is("MacBook Pro")))

                // 2. Collection (Set)
                .andExpect(jsonPath("$.itemSet", hasSize(2)))
                // 使用 jsonPath features checking if MacBook Pro is in the names list
                .andExpect(jsonPath("$.itemSet[*].goodsName", hasItems("MacBook Pro", "iPhone 15")))

                // 3. Map
                .andExpect(jsonPath("$.itemMap.itemKey.goodsId", is(101)))
                .andExpect(jsonPath("$.itemMap.itemKey.goodsName", is("MacBook Pro")))

                // 4. Map of Collection
                .andExpect(jsonPath("$.nestedItemMap.outerKey", hasSize(2)))
                .andExpect(jsonPath("$.nestedItemMap.outerKey[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$.nestedItemMap.outerKey[1].goodsName", is("iPhone 15")))

                // 5. Deep Nested Map (Map of Map of List)
                .andExpect(jsonPath("$.deepNestedItemMap.outerKey.innerKey", hasSize(2)))
                .andExpect(jsonPath("$.deepNestedItemMap.outerKey.innerKey[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$.deepNestedItemMap.outerKey.innerKey[1].goodsName", is("iPhone 15")));
    }

    /**
     * 测试 3：包装格式 Result<List<OrderPO>> 形式，自动就地转义填充
     * - 验证切面对包装类的支持，不仅要解包翻译，还要装包还原
     */
    @Test
    public void testWrappedOrders() throws Exception {
        mockMvc.perform(get("/orders/wrapped")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.msg", is("success")))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id", is(1)))
                .andExpect(jsonPath("$.data[0].userName", is("张三")))
                .andExpect(jsonPath("$.data[0].items[0].goodsName", is("MacBook Pro")));
    }

    /**
     * 测试 4：归档订单 List<ArchiveOrderPO> 形式，【同样且完全自动地】就地转义填充
     * - 验证多数据源、多 PO 支持：不同数据源下（归档 PO）依然能够完美就地翻译
     */
    @Test
    public void testArchiveOrders() throws Exception {
        mockMvc.perform(get("/orders/archive")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // 归档订单 10 校验：用户 3 -> 王五
                .andExpect(jsonPath("$[0].id", is(10)))
                .andExpect(jsonPath("$[0].userId", is(3)))
                .andExpect(jsonPath("$[0].userName", is("王五")))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].id", is(10)))
                .andExpect(jsonPath("$[0].items[0].goodsId", is(102)))
                .andExpect(jsonPath("$[0].items[0].goodsName", is("iPhone 15")))
                // 归档订单 11 校验：用户 2 -> 李四
                .andExpect(jsonPath("$[1].id", is(11)))
                .andExpect(jsonPath("$[1].userId", is(2)))
                .andExpect(jsonPath("$[1].userName", is("李四")))
                .andExpect(jsonPath("$[1].items", hasSize(1)))
                .andExpect(jsonPath("$[1].items[0].id", is(11)))
                .andExpect(jsonPath("$[1].items[0].goodsId", is(101)))
                .andExpect(jsonPath("$[1].items[0].goodsName", is("MacBook Pro")));
    }

    /**
     * 测试 5：变态级超深嵌套转义测试 (Map of Map of List, List of Map of Set, Translatable Key Map of Map of List)
     * - 验证 100% 任意维度、任意层级的复杂嵌套映射及翻译逻辑支持
     */
    @Test
    public void testExtremeNestedTranslation() throws Exception {
        mockMvc.perform(get("/orders/extreme")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(999)))

                // Nest 1 校验 (Map of Map of List)
                .andExpect(jsonPath("$.poMapMapList.outerKey.innerKey", hasSize(2)))
                .andExpect(jsonPath("$.poMapMapList.outerKey.innerKey[0].goodsName", is("MacBook Pro")))
                .andExpect(jsonPath("$.poMapMapList.outerKey.innerKey[1].goodsName", is("iPhone 15")))

                // Nest 2 校验 (List of Map of Set)
                .andExpect(jsonPath("$.poListMapSet[0].setKey", hasSize(2)))
                .andExpect(jsonPath("$.poListMapSet[0].setKey[*].goodsName", hasItems("MacBook Pro", "iPhone 15")))

                // Nest 3 校验 (Map with translatable Key and nested Map of List Value)
                // 在 JSON 中，由于 Map 的 Key 是对象，Jackson 会把 Key 序列化成 String（即 OrderItemVO.toString()，除非有自定义 KeySerializer）
                // 我们主要校验 Map 的 Value 里面是否成功进行了属性翻译
                .andExpect(jsonPath("$.poTransMapComplex.*.valueKey[0].goodsName", hasItem("MacBook Pro")));
    }
}
