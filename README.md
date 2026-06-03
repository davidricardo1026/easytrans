# EasyTrans：基于 MapStruct + APT 的超高性能关联 ID 自动批量转义框架

在企业级 Java 开发（如电商、金融、OA 等系统）中，我们经常遇到 **PO ➡️ VO 转换与关联数据转义** 的典型场景：
数据库持久化对象（`PO`/`Entity`）中通常只存有关联表的 ID（如 `userId`、`goodsId`）。而在向前端或客户端返回视图对象（`VO`/`DTO`
）时，需要将这些 ID 转义（填充）为更直观的代码（`code`）或名称（`name`），如 `userName`、`goodsName`。

**EasyTrans** 巧妙地结合了 **MapStruct 的类型安全映射** 与 **Java 编译期注解处理器 (APT)**，在**编译期**
完成全部的代码生成工作，带来了前所未有的极致性能与丝滑的开发体验。

---

## 🔥 EasyTrans 核心亮点

* **极致性能（接近物理极限）**：
  **零反射、零动态代理**。所有 ID 收集和属性拷贝、回填，全部在**编译期**通过 APT 生成原生 Java 代码硬编码执行。执行效率等同于纯手写，物理性能极限。
* **两阶段执行，完美消灭 N+1 问题**：
    * **第一阶段（ID 收集）**：自动生成的桥接转换器以硬编码形式**递归、高吞吐**地收集所有 PO（含深层嵌套集合，如
      `Order -> OrderItem`）里的关联 ID。
    * **批量加载（Batch Load）**：将收集到的 ID 分类去重，一次性分发给各业务实现的 `TranslationFeeder` 进行批量 In 查询。
    * **第二阶段（硬编码回填）**：利用 MapStruct，零反射极速进行属性拷贝并从上下文中检索回填对应名称。
* **100% 零侵入（Zero-Intrusive）**：
  结合 Spring MVC 的 `ResponseBodyAdvice` 切面，Controller 层的业务代码**完全不需要知道转义的存在**。只管查出 `PO`（或包装类
  `Result<PO>`）并返回，框架会自动将其升级为完美的转义 `VO`。
* **多数据源支持（一对多映射）**：
  VO 的 `@TranslateFrom` 注解支持配置多个 Source PO。例如活跃订单 `OrderPO` 与归档订单 `ArchiveOrderPO` 可以自动转义并升级到同一个
  `OrderVO` 视图中。
* **深层嵌套对象支持**：
  完美的树形或嵌套集合结构支持（例如 `OrderPO` 内嵌套了 `List<OrderItemPO>`），框架能自动在深层递归收集 `goodsId` 并完成多级转换。

---

## 🛠️ 核心三件套（两个注解，一个接口）

EasyTrans 的运转和使用完全依赖以下三个核心组件的精妙配合：

### 1. `@TranslateFrom` (注解)

* **作用**：标注在目标/展示类（`VO` / `DTO`）上。
* **职责**：声明该目标类支持从哪些源类（`PO` / `Entity`）转换映射而来。支持配置多个源类。
* **配合**：APT 编译器扫描到该注解后，会自动生成对应的 MapStruct Mapper（类型拷贝）和 Bridge 桥接类（ID 收集与回填）。默认会将生成的注册表类输出至
  **`[该 VO 类所在的包].generated`** 包下，而生成的 Mapper 和 Bridge 转换器输出至 **`[该 VO 类所在的包].generated.mapper`
  ** 包下。这种“邻近生成”的设计既防污染又符合直觉，通常无需任何修改。

### 2. `@TranslateField` (注解)

* **作用**：标注在目标类（`VO` / `DTO`）的**转义属性**上。
* **职责**：声明该属性是一个转义属性。通过 `source` 指定包含 Key/ID 的源属性名，通过 `type` 指定转义服务类型标识。
* **配合**：收集阶段，Bridge 类会根据它硬编码、无反射地抓取 ID；回填阶段，MapStruct 会将对应的转义值填入此属性。

### 3. `TranslationFeeder` (接口)

* **作用**：用户实现的 **SPI 数据加载接口**。
* **职责**：定义某个 `type` 对应的批量加载逻辑（如根据一批用户 ID 批量查询数据库/RPC，并返回 `Map<Id, Name>`）。
* **配合**：在 ID 收集完毕后，框架会自动将收集到的 ID 分组分发给对应 `type` 的 Feeder 实现类，进行 **In 批量加载**（秒杀 N+1
  问题）。

---

## ⚙️ 进阶：配置生成代码包名与支持非 Spring 环境

EasyTrans 提供了极其灵活的代码生成配置，支持自定义包名、追加包名后缀，甚至支持在 **非 Spring 环境** 下原生运行！

### 1. 配置代码生成包名

我们将其分为 **后缀配置** 与 **全包名限定配置**（全包名限定配置具有更高优先级）。

#### A. 后缀配置（默认推荐）

默认情况下，EasyTrans 会在 **VO 类所在的当前包** 后追加指定的后缀来生成代码，无需手动指定冗长的全包名，非常适合多模块或大项目。

* **`easytrans.generated.package.suffix`**: 注册表所在的包后缀（默认为 `generated`）。生成后的全包名为
  `[VO所在的包].generated`。
* **`easytrans.generated.mapper.package.suffix`**: Mapper 和 Bridge 转换器所在的包后缀（默认为 `generated.mapper`
  ）。生成后的全包名为 `[VO所在的包].generated.mapper`。

#### B. 全包名限定配置（高优先级）

如果你希望将所有生成的代码统一归集到全局某一个包下，你可以配置固定的全包名（配置后会覆盖后缀配置）：

* **`easytrans.generated.package`**: 自定义主注册表（`GeneratedTranslationRegistry`）所在的**固定全包名**。
* **`easytrans.generated.mapper.package`**: 自定义 MapStruct Mapper 与 Bridge 桥接类所在的**固定全包名**。

---

### 2. 支持非 Spring 环境（轻量级无依赖运行）

EasyTrans 默认会往生成的 Mapper、Bridge 和 Registry 类上加上 `@Component` 和 `componentModel = "spring"`，以便在 Spring
环境中自动组装。

如果你的项目是**非 Spring 架构**（如纯 Java、Vert.x、Micronaut、Quarkus 或轻量级 Dubbo 消费者），你只需要配置 *
*`easytrans.enable.spring=false`**。

#### 开启非 Spring 环境下的代码生成变化：

* **Mapper 转换器**：去除 `@Mapper(componentModel = "spring")`，退化为原生 MapStruct 的默认工厂加载。
* **Bridge 桥接器**：去除 `@Component`，变为普通的 POJO 类。其内部自动采用 `org.mapstruct.factory.Mappers.getMapper` 无缝加载
  Mapper 实现。
* **Registry 注册表**：去除 `@Component`，并在内部采用**直接 new 实例**的形式完成对全部 Bridge 的实例化，不再使用构造器参数注入。你只需直接
  `new GeneratedTranslationRegistry()` 即可零感知极速使用！

---

### Maven 配置示例：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <compilerArgs>
            <!-- 场景 A：我想使用后缀配置来自定义，比如改为 .gen 和 .gen.map -->
            <arg>-Aeasytrans.generated.package.suffix=gen</arg>
            <arg>-Aeasytrans.generated.mapper.package.suffix=gen.map</arg>
            
            <!-- 场景 B：支持非 Spring 环境（关闭 Spring 组件注解，不加则默认为 true） -->
            <arg>-Aeasytrans.enable.spring=false</arg>
            
            <!-- 场景 C：我想限制成全局唯一的全包名（这会覆盖上面的后缀配置） -->
            <!--
            <arg>-Aeasytrans.generated.package=com.yourcompany.project.easytrans.generated</arg>
            <arg>-Aeasytrans.generated.mapper.package=com.yourcompany.project.easytrans.generated.mapper</arg>
            -->
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>io.github.easytrans</groupId>
                <artifactId>easytrans-processor</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

---

## 💻 极简配合与使用示例

### 1. 定义 VO 目标类 (使用 `@TranslateFrom` 和 `@TranslateField`)

```java
// 声明此 VO 支持从常规订单 OrderPO 映射而来
@Data
@TranslateFrom(OrderPO.class) 
public class OrderVO {
    private Long id;
    private Long userId; // 源属性

    // 声明 userName 的数据来源于 userId，转义业务标识为 USER_SERVICE
    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName; 
    
    // 自动支持嵌套列表的深层递归转义！
    private List<OrderItemVO> items;
}
```

### 2. 实现数据加载器 (实现 `TranslationFeeder` SPI)

```java
// 实现 SPI 接口并注入为 Spring Bean。
@Component
public class UserTranslationFeeder implements TranslationFeeder {

    @Autowired
    private UserMapper userMapper;

    @Override
    public String getType() {
        return "USER_SERVICE"; // 须与 @TranslateField.type 保持一致
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        // 批量 In 查询（支持本地 DB 批量、Dubbo、Feign RPC 或 Redis），返回 Map<ID, 展示名称>
        List<UserPO> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(UserPO::getId, UserPO::getName));
    }
}
```

### 3. 运行期执行与 100% 零侵入 Controller

通过在 Web 层配置一个简单的 `ResponseBodyAdvice` 全局拦截器（在其中调用
`TranslationExecutor.translate(sourceList, mapper)`），可彻底解放业务 Controller 的逻辑。

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 100% 零侵入！
     * Controller 只管返回干净的数据库 PO 集合，甚至无需感知 VO 的存在。
     * 切面拦截后自动在底层：递归提取 ID -> Feeder 批量拉取数据 -> MapStruct 极速属性拷贝与回填 -> 升级为 OrderVO 返回。
     */
    @GetMapping
    public List<OrderPO> listOrders() {
        return orderMapper.selectList(null); 
    }
}
```

---

## 🔄 运行期核心原理

```text
  [Controller 返回 PO 列表]
             |
             v (ResponseBodyAdvice 拦截)
  1. ID 收集阶段 (Bridge.extractAllIds) -----------------> 编译期硬编码递归、极速收集嵌套关联 ID
                                                                       |
                                                                       v
  2. 批量加载阶段 (Feeder.batchLoad) <------------------ 聚合 ID 去重分类，一次性分发批量 IN 查询
             |
             v (填充上下文)
  3. MapStruct 极速回填 & 属性拷贝 (toTargetList)
             |
             v
  [输出完美的转义 VO 列表给前端]
```

---

---

## 📂 多模块 Demo 项目结构说明

为了全方位、多维度地向您展现 EasyTrans 在真实生产、以及不同底层架构环境下的适用性，我们在 `easytrans-demo` 目录下精心构建了一个
**多模块项目**。它分为：

```text
easytrans-demo
├── easytrans-demo-spring   # Spring Web MVC 环境：全自动 Spring 模式。支持 MyBatis-Plus 数据库查询与 BodyAdvice 零侵入转义。
├── easytrans-demo-main     # 纯 Java Main 环境：全自动非 Spring 模式。演示在没有任何 Spring 容器的情况下，手动驱动转义的极简流程。
├── easytrans-demo-suffix   # 测试模块：配置包后缀 (-Aeasytrans.generated.package.suffix, -Aeasytrans.generated.mapper.package.suffix)。
├── easytrans-demo-full     # 测试模块：配置全局唯一的固定全包名 (-Aeasytrans.generated.package, -Aeasytrans.generated.mapper.package)。
└── easytrans-demo-both     # 测试模块：同时配置后缀与全包名，测试全包名限定的高优先级覆盖规则。
```

### 1. `easytrans-demo-spring` (Spring Boot 3 + MyBatis-Plus 示例)

* **职能**：典型企业级 Spring 整合，采用常规 Spring 模式进行转义。
* **开发与配置**：它的 VO 声明在 `io.github.easytrans.demo.entity` 包中。它的 `pom.xml` 中使用了默认的编译配置（未配置
  `easytrans.enable.spring`，默认为 `true`），生成的 Mapper、Bridge 转换器、Registry 注册表自动带有 Spring `@Component`
  注解，完全由 Spring IoC 自动组装。
* **拦截器配置**：配置了 `AutoTranslationAdvice` (继承 `ResponseBodyAdvice`)，在 API 响应的第一时间自动拦截 `PO`（或
  `Result<PO>`）并原地升级为 `VO`。Controller 业务层 100% 干净，只跟数据库实体打交道。

### 2. `easytrans-demo-main` (纯 Java 原生环境示例)
* **职能**：微服务、轻量级 RPC 客户端、或者非 Spring Web 框架下的使用。
* **开发与配置**：它的 VO 声明在 `io.github.easytrans.demo.main.entity` 包中。它的 `pom.xml` 中配置了
  `-Aeasytrans.enable.spring=false`，因此编译该模块时，生成的类**完全没有 Spring 依赖**。
* **运行机制**：完全在 `main` 方法中自闭环运行。你只需：
  1. 通过 `new GeneratedTranslationRegistry()` 瞬时建立注册映射。
  2. 手动创建 `TranslationFeeder` 的 mock/常规实现实例。
  3. 执行 `translationExecutor.translate(pos, mapper)` 即可完美享受两阶段批量高性能 ID 转义。

### 3. 配置测试模块

* **`easytrans-demo-suffix`**: 测试在 VO 所在的包追加自定义后缀，编译后生成物自动存放于 VO 包下的子包中，彻底隔离并分类。
* **`easytrans-demo-full`**: 测试强制全局全包名配置模式，不采用包后缀邻近生成，直接由用户掌控目标生成包。
* **`easytrans-demo-both`**: 测试全包名配置与后缀配置混合传入，验证全包名配置拥有更高优先级的完全覆盖机制。

---

## 📄 开源许可证 (License)

EasyTrans 采用 [Apache License 2.0](LICENSE) 许可协议，欢迎贡献代码与提报 Issue！
