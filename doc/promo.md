# 告别反射与 N+1 查询！EasyTrans：基于 MapStruct + APT 的超高性能关联 ID 自动批量转义框架

在企业级 Java 开发（如电商、金融、OA、ERP 等系统）中，我们几乎每天都会遇到 **PO ➡️ VO 转换与关联数据转义** 的典型场景。

例如：数据库实体 `OrderPO` 中存的是 `userId`、`goodsId`。而在返回给前端的视图对象 `OrderVO` 中，我们需要将其翻译为
`userName`、`goodsName`。

传统的解决方案要么非常笨重，要么极其缓慢：

1. **纯手写硬编码**：在 Service 循环里查数据库，写出一大堆面条代码，开发效率低下且极易漏掉漏翻译。
2. **在 Loop 中循环查询（N+1 问题）**：写出在 `for` 循环中查询 DB/RPC 的低级代码，导致数据库连接瞬间被撑爆，系统性能暴跌。
3. **基于反射/动态代理的运行时转义**：虽然用注解省了事，但大量的**反射**和**切面拦截**在面对高并发、大数量级翻译时，会导致严重的
   CPU 飙升与高延迟。

---

## 🚀 EasyTrans：把性能压榨到物理极限的“零反射”转义框架

**EasyTrans** 另辟蹊径，巧妙地将 **MapStruct 的类型安全映射** 与 **Java 编译期注解处理器 (APT)** 结合。它摒弃了传统的运行时动态解析，选择在
**编译期** 自动生成全部的原生转换逻辑，带来了**媲美纯手写**的物理极限性能和**100% 零侵入**的开发体验。

项目已开源，Maven 中央仓库正式版 `1.0.0` 现已同步上架！

* **GitHub 仓库**：[davidricardo1026/easytrans](https://github.com/davidricardo1026/easytrans)

---

## 🔥 核心黑科技解析

### ⚡ 编译期硬编码生成，把性能拉满

EasyTrans **零反射、零动态代理**。在你的项目编译时，APT（编译期注解处理器）会通过 AST 语法树动态分析你的注解，并自动生成原生、高吞吐的桥接转换类。所有的
ID 抽取和属性回填代码在运行期等同于你一行行手写的纯 Java 代码，物理性能达到绝对极限。

### 🛡️ 完美的“两阶段执行”：优雅消灭 N+1 问题

EasyTrans 采用业界先进的 **ID 聚合批量加载** 机制：

1. **第一阶段（ID 收集）**：自动生成的 Bridge 桥接转换器会以硬编码形式，瞬间**递归**、**高吞吐**地抽取你返回对象（包含深层嵌套、Map、List
   集合）中所有的关联键。
2. **批量加载（SPI Batch Load）**：将抽取的 ID 自动按类型去重、合并，一次性分发给你实现的 `TranslationFeeder` 进行**单次
   Batch In 查询**（支持本地 DB 批量、Dubbo/Feign RPC 批量，甚至 Redis 批量拉取）。
3. **第二阶段（硬编码回填）**：利用 MapStruct 以零反射的高性能瞬间进行属性拷贝，并将批量拉取到的数据硬编码回填进展示字段中。

### 🎨 3D 无死角：无限层级递归、复杂 Map / 集合 100% 完美吞噬

平常开发中，最让人头疼的就是嵌套对象的转义（例如 `OrderVO` 嵌套了 `List<OrderItemVO>`，或者 `Map<String, List<GoodsVO>>`
等多维复杂结构）。
EasyTrans 在编译期会自顶向下**无限层级递归**地扫描 AST，自动生成层层嵌套的 loops 与 `EntrySet` 的硬编码迭代。无论是单值对象、任何
Collection 类型，还是多维/双重 Map 结构，均能完美识别并在编译期锁死转换链，绝不留 1% 的死角！

### 🧼 100% 零侵入，优雅护眼

结合 Spring MVC 的 `ResponseBodyAdvice` 切面拦截，你的业务 Controller 层甚至根本不需要知道转义框架的存在。

```java

@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderMapper orderMapper;

    // 100% 零侵入！Controller 只管返回干净的数据库 PO 集合，切面拦截后自动在运行期平滑升级为 VO 并完成极速转义
    @GetMapping
    public List<OrderPO> listOrders() {
        return orderMapper.selectList(null);
    }
}
```

---

## 🛠️ 极简使用：核心三件套

### 1. 标注展示类：`@TranslateFrom`

告诉框架该 VO 支持从哪些源 PO 转换而来，支持配置多个 Source 实现单视图模型、多数据源支持：

```java

@Data
@TranslateFrom({OrderPO.class, ArchiveOrderPO.class}) // 自动支持从活跃订单 PO 和归档订单 PO 转换
public class OrderVO {
    private Long id;
    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE") // 声明转义来源与业务类型
    private String userName;

    // 无限层级嵌套、Map、集合，框架在编译期自动为你递归生成转义逻辑！
    private Map<String, Map<String, List<OrderItemVO>>> deepNestedItemMap;
}
```

### 2. 标注转义属性：`@TranslateField`

标注在转义属性上，声明该属性所依赖的 key 以及转义逻辑对应的业务 `type` 标识。

### 3. 实现数据加载器：实现 `TranslationFeeder`

实现 SPI 数据加载接口并注入为 Bean。在这里你只需关心如何通过这批 ID 进行单次批量 In 查询：

```java

@Component
public class UserTranslationFeeder implements TranslationFeeder {
    @Autowired
    private UserMapper userMapper;

    @Override
    public String getType() {
        return "USER_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        List<UserPO> users = userMapper.selectBatchIds(ids); // 一次性批量 In 查出，消除 N+1
        return users.stream().collect(Collectors.toMap(UserPO::getId, UserPO::getName));
    }
}
```

---

## 🔌 强大的双版本 Starter 精准适配

为了照顾不同企业技术选型的兼容性，EasyTrans 提供了极其规范的双版本自动装配 Starter：

* **Spring Boot 3.x & 4.x (JDK 17+)**：
  ```xml
  <dependency>
      <groupId>io.github.davidricardo1026</groupId>
      <artifactId>easytrans-spring-boot-starter</artifactId>
      <version>1.0.0</version>
  </dependency>
  ```
* **Spring Boot 2.x (JDK 17+ / 仅作 Spring Boot 2 兼容适配)**：
  ```xml
  <dependency>
      <groupId>io.github.davidricardo1026</groupId>
      <artifactId>easytrans-spring-boot2-starter</artifactId>
      <version>1.0.0</version>
  </dependency>
  ```

> **🛡️ 设计细节**：在复杂的企业级多模块依赖拓扑中，翻译注册表极易与持久层 Service/Mapper 形成令人崩溃的**循环引用**
> 。EasyTrans 两个版本的 Starter 均在底层整合了 `ObjectProvider` 与 延迟加载 `Supplier` 组合拳，**在框架设计层面就直接屏蔽并消灭了
Spring 初始化时的 `BeanCurrentlyInCreationException` 异常**！

---

## 🌟 总结：开发效率与系统性能双重起飞

通过在**编译期**榨干 MapStruct + APT 的全部潜能，EasyTrans 在真正意义上做到了：

* 业务开发只管查 PO，Controller 一行干净返回，开发效率大幅提高。
* 运行期零反射、高吞吐 ID 收集与批量 In 查询，消灭 N+1，性能提升数倍，CPU 稳如磐石。

赶紧在你的项目里用起来吧，从此告别脏乱差的手写翻译和昂贵的运行时反射，让你的代码像诗一样优雅，像跑车一样迅猛！

* 欢迎在 GitHub 提交 Issue 和 PR：[davidricardo1026/easytrans](https://github.com/davidricardo1026/easytrans)
