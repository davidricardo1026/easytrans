# 告别反射与 N+1 查询！EasyTrans：超高性能关联 ID/Code 自动批量就地翻译框架正式开源！

在企业级 Java 开发（如电商、金融、OA、ERP 等系统）中，我们几乎每天都会遇到 **关联数据就地翻译** 的典型场景。

例如：数据库实体或数据传输对象 `OrderPO` 中存的是 `userId`、`goodsId`。而在向前端或客户端输出时，我们需要将其快速翻译为
`userName`、`goodsName`。

传统的解决方案往往存在极大的缺陷：

1. **纯手写硬编码**：在 Service 循环里查数据库，写出一大堆面条代码，开发效率低下且极易漏掉、漏翻译。
2. **在 Loop 中循环查询（N+1 问题）**：写出在 `for` 循环中查询 DB/RPC 的低级代码，导致数据库连接瞬间被撑爆，系统性能暴跌。
3. **基于反射/动态代理的运行时转义**：虽然用注解省了事，但在高并发、大数量级翻译时，频繁的反射开销会导致 CPU 性能暴跌与产生高延迟。
4. **反射带来的性能损耗**：即使引入了切面和动态代理，依然需要对实体类的每个字段做反射属性读取与注入，耗费系统资源。

---

## 🚀 EasyTrans：就地翻译 (In-place Translation) 带来极限性能与极简开发

**EasyTrans** 另辟蹊径，带来划时代的**就地翻译 (In-place Translation)** 模式。
你不需要定义多余的对象拷贝或转换插件，更不需要写冗长复杂的转换逻辑。数据直接就在原实体类（PO / Entity / DTO）上进行就地转义填充！

EasyTrans 在**编译期**通过 APT 自动生成原生、高吞吐的桥接类（Bridge）。所有的 ID 收集、嵌套递归和属性回填代码在运行期等同于你一行行手写的纯
Java 代码（纯 for 循环和 setter 调用），物理性能达到绝对极限，彻底释放你的 CPU 资源。

项目已开源，Maven 中央仓库正式版 `2.0.0` 现已同步上架！

* **GitHub 仓库**：[davidricardo1026/easytrans](https://github.com/davidricardo1026/easytrans)

---

## 🔥 核心黑科技解析

### ⚡ 编译期硬编码生成，把性能拉满

EasyTrans **100% 零反射、零动态代理**。在你的项目编译时，APT（编译期注解处理器）会自动为你生成极其纯净、高吞吐的原生桥接类。ID
抽取和属性回填代码等同于纯手写的 for 循环和 setter 直接赋值。物理性能压榨到绝对极限。

### 🛡️ 完美的“两阶段执行”：优雅消灭 N+1 问题

EasyTrans 采用批量聚合加载机制：

1. **第一阶段（ID 收集）**：自动生成的 Bridge 会以硬编码形式，瞬间**递归**、**高吞吐**地抽取你返回对象（包含深层嵌套、Map、List
   集合）中所有的关联键。
2. **批量加载（SPI Batch Load）**：将抽取的 ID 自动按类型去重、合并，一次性分发给你实现的 `TranslationFeeder` 进行**单次
   Batch In 查询**（支持本地 DB 批量、Dubbo/Feign RPC 批量，甚至 Redis 批量拉取）。
3. **第二阶段（硬编码回填）**：将批量拉取到的数据，通过纯硬编码的 setter 方法就地秒级回填到实体的对应转义展示属性中。

### 🎨 无限层级递归、复杂 Map / 集合 100% 完美就地填充

平常开发中，最让人头疼的就是嵌套对象的转义（例如 `Order` 嵌套了 `List<OrderItem>`，或者 `Map<String, List<Goods>>`
等多维复杂结构）。
EasyTrans 在编译期会自顶向下**无限层级递归**地扫描，自动生成层层嵌套的 loops 与 `EntrySet` 的硬编码迭代。无论是单值对象、任何
Collection 类型，还是多维/双重 Map 结构，均能完美识别并在编译期锁死就地填充链，绝不留 1% 的死角！

### 🧼 100% 零侵入，100% 契约对应，优雅护眼
结合 Spring MVC 的 `ResponseBodyAdvice` 切面拦截，你的业务 Controller 层甚至根本不需要知道转义框架的存在。
最重要的是：**Controller 声明返回什么类，响应出来的就是什么类，类型契约 100% 对应！** 只是在 HTTP 响应序列化输出的前一秒，里面的展示文本（如
`userName`）已经被自动就地填好了！

```java
@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderMapper orderMapper;

    // 100% 零侵入！Controller 声明返回纯净的 List<OrderPO>
    // 切面拦截后自动在底层：递归提取 ID -> Feeder 批量拉取 -> 零反射就地填充！
    // 接口签名与实际响应 100% 契约一致！
    @GetMapping
    public List<OrderPO> listOrders() {
        return orderMapper.selectList(null);
    }
}
```

---

## 🛠️ 极简使用：核心三件套

### 1. 标注实体类：`@Translatable`
只需一个简单的注解，告诉框架该类支持就地关联翻译：

```java
@Data
@Translatable
public class OrderPO {
    private Long id;
    private Long userId;

    @TranslateField(source = "userId", type = "USER_SERVICE") // 声明转义来源与业务类型
    private String userName;

    // 无限层级嵌套、Map、集合，只要关联实体（如 OrderItemPO）也标注了 @Translatable，编译期自动生成递归填充逻辑！
    private Map<String, Map<String, List<OrderItemPO>>> deepNestedItemMap;
}
```

### 2. 标注转义属性：`@TranslateField`

在展示文本属性上配置，通过 `source` 指定包含关联 ID/Code 的属性，通过 `type` 指定翻译对应的 Feeder 标识。

### 3. 实现数据加载器：实现 `TranslationFeeder`
实现 SPI 并注入为 Bean，轻松支持本地、微服务 RPC、缓存等各类批量拉取方式。

```java
@Component
public class UserTranslationFeeder implements TranslationFeeder {
    @Autowired
    private UserMapper userMapper;

    @Override
    public String getType() {
        return "USER_SERVICE"; // 对应 @TranslateField 中的 type
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        // 一次性批量 In 查库/RPC，消灭 N+1！
        List<UserPO> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(UserPO::getId, UserPO::getName));
    }
}
```

### 4. 依赖引入与编译期配置

> **💡 开箱即用，配置全可选：**  
> 默认不加任何编译器配置时，EasyTrans 会自动将 Bridge 类与 Registry 注册表类安全隔离生成于 `.generated` 和
`.generated.registry` 隔离子包下。常规项目直接引入 Starter 依赖即可，无须配置任何编译器参数！

#### A. Maven 配置示例

直接引入 Starter 依赖：

```xml
<!-- Spring Boot 3.x & 4.x (JDK 17+) 选择： -->
<dependency>
    <groupId>io.github.davidricardo1026</groupId>
    <artifactId>easytrans-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>

        <!-- Spring Boot 2.x (JDK 17+) 选择： -->
        <!--
        <dependency>
            <groupId>io.github.davidricardo1026</groupId>
            <artifactId>easytrans-spring-boot2-starter</artifactId>
            <version>2.0.0</version>
        </dependency>
        -->
```

在 `maven-compiler-plugin` 中配置编译期注解处理器：

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>io.github.davidricardo1026</groupId>
                <artifactId>easytrans-processor</artifactId>
                <version>2.0.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

#### B. Gradle 配置示例 (折叠展示)

<details>
<summary><b>点击展开 / 折叠 Gradle 配置 (Groovy DSL)</b></summary>

```groovy
dependencies {
    // Spring Boot 3/4 选择:
    implementation 'io.github.davidricardo1026:easytrans-spring-boot-starter:2.0.0'
    // Spring Boot 2 选择:
    // implementation 'io.github.davidricardo1026:easytrans-spring-boot2-starter:2.0.0'

    compileOnly 'org.projectlombok:lombok:1.18.30'

    // 声明注解处理器 (APT)
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'io.github.davidricardo1026:easytrans-processor:2.0.0'
}
```

</details>

---

## 🔌 工业级多版本 Starter 与多模块生态支持

EasyTrans 针对 Spring Boot 2.x 和 3.x/4.x 提供高度规范且一致的 Starter 自动装配。通过引入延迟加载，**100% 消灭了 Spring
初始化期间可能由于循环引用导致的启动失败**。

同时，EasyTrans 也完美支持 **非 Spring 运行环境**。只需在编译Args中配置 `-Aeasytrans.enable.spring=false`，生成的 Bridge
类将不带任何 Spring 注解。你只需直接 `new GeneratedTranslationRegistry()` 即可，极其方便微服务、纯 Java 或其他轻量级非
Spring 框架无痛使用！

## 📄 结语：欢迎体验！

如果你厌倦了冗长的对象拷贝、反胃的 N+1 面条代码，或是因反射导致的高并发延迟，**EasyTrans** 将是为你量身定制的破局利器。

欢迎访问 GitHub 仓库，体验超高性能就地转义的极致快感！
[davidricardo1026/easytrans](https://github.com/davidricardo1026/easytrans)
