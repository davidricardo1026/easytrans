# EasyTrans：超高性能关联 ID 自动批量就地翻译框架 (In-place Translation)

在企业级 Java 开发（如电商、金融、OA、ERP 等系统）中，我们经常遇到 **关联数据转义/翻译** 的典型场景：
数据库实体或传输对象中通常只存有关联表的 ID 或 Code（如 `userId`、`goodsId`）。而在向前端、客户端返回或在业务处理时，需要将这些
ID/Code 翻译为更直观的展示文本（如 `userName`、`goodsName`）。

传统方案要么在循环中查库（造成可怕的 N+1 问题），要么需要使用复杂的反射/切面在运行期动态解析，极大损耗 CPU 性能并导致高延迟。

**EasyTrans 专注于零反射、就地翻译 (In-place Translation) 方案！**
直接在同一个实体对象上进行就地转义填充！我们利用 **Java 编译期注解处理器 (APT)**，在**编译期**自动为你生成纯硬编码的、零反射、零动态代理的
ID 抽取与数据就地回填桥接器（Bridge），带来前所未有的极致性能与丝滑、干净的开发体验。

---

## 🔥 EasyTrans 核心亮点

* **纯粹就地翻译，零对象拷贝**：
  你再也不需要为转义多写任何冗余的类！只有一个实体类，属性直接就地填充。项目结构瞬间缩减，契约高度一致！
* **极致性能（接近物理极限）**：
  **100% 零反射、零动态代理**。所有的 ID 收集、递归遍历和最终的值回填，全部在**编译期**由 APT 注解处理器生成原生 Java
  代码（纯手写般的 for 循环和 setter 赋值）执行。执行效率等同于纯手写，物理性能极限。
* **两阶段执行，完美消灭 N+1 问题**：
  * **第一阶段（ID 收集）**：自动生成的桥接类（Bridge）以硬编码形式**递归、高吞吐**地收集实体（含任何深层嵌套，如
    `Order -> OrderItem`）里的关联 ID/Code。
  * **批量加载（Batch Load）**：将收集到的 ID 分类去重，一次性分发给各业务实现的 `TranslationFeeder` 进行批量 In 查询（秒杀
    N+1 查询问题）。
  * **第二阶段（硬编码回填）**：直接从上下文中检索出翻译好的文本，通过纯硬编码的 setter 方法就地秒级回填到实体的对应展示属性上。
* **100% 零侵入与 100% 接口契约一致**：
  结合 Spring MVC 的 `ResponseBodyAdvice` 切面，Controller 层的业务代码**完全不需要知道转义的存在**。Controller
  声明返回什么类，实际响应就是什么类，只是里面的转义属性在 HTTP 响应序列化前，被 Advice 自动且完美地就地填上了！契约 100%
  吻合！
* **三大基础嵌套结构与无限层级递归完美就地填充 (Single, Collection, Map)**：
  无论是单值对象 (嵌套实体)、任何集合类型 (如 `List`/`Set`/`Collection`)、还是极复杂的树状多维嵌套 Map 结构 (例如
  `Map<String, Map<String, List<OrderItem>>>`)，框架都能在**编译期**自动识别，并生成**无限层级递归**
  的极速硬编码就地提取和回填逻辑 (无反射嵌套 Loop/EntrySet)，100% 完美吞噬任意变态深度的复杂数据结构。

---

## 🛠️ 核心三件套（两个注解，一个接口）

### 1. `@Translatable` (注解)

* **作用**：标注在实体类（`PO` / `Entity` / `DTO`）上。
* **职责**：声明该类是一个可进行就地翻译的对象。APT 编译器扫描到该注解后，会自动为其生成对应的 Bridge 桥接类。
* **生成路径**：默认会将生成的注册表类输出至该类所在的包下的 **`generated.registry`** 包中，桥接类输出至 **`generated`**
  包中。

### 2. `@TranslateField` (注解)

* **作用**：标注在实体类的**目标属性**（用于存放翻译文本，如 `userName`）上。
* **职责**：声明该属性是一个需要就地回填的转义属性。
  * `source`：指定本实体类中包含关联 ID/Code 的源属性名（如 `userId`）。
  * `type`：指定转义服务类型标识（如 `USER_SERVICE`）。

### 3. `TranslationFeeder` (接口)

* **作用**：用户实现的 **SPI 数据加载接口**。
* **职责**：定义某个 `type` 对应的批量加载逻辑（如根据一批用户 ID 批量查询数据库/RPC，并返回 `Map<Id, Name>`）。
* **配合**：在 ID 收集完毕后，框架会自动将收集到的 ID 分组分发给对应 `type` 的 Feeder 实现类，进行 **In 批量加载**。

---

## ⚙️ 进阶：配置生成代码包名与支持非 Spring 环境

> **⚠️ 重点提示（开箱即用，配置可选）：**  
> **所有的编译器选项（`compilerArgs`）都是完全可选的！**  
> 默认不加任何配置时，EasyTrans 的开箱即用行为已极度优化，足以完美应对 99.9% 的企业级生产项目：
> * **Bridge 桥接类**：自动生成在实体类所在包下的 `.generated` 子包中。
> * **Registry 注册表**：自动生成在实体类所在包下的 `.generated.registry` 子包中。
> * **Spring 环境**：默认自动支持并加上 Spring 的 IOC 组件装配注解。  
    > **因此，在一般情况下，你只需要直接引入 Starter 依赖，即可无痛享受就地关联翻译功能！**

### 1. 配置代码生成包名（仅在有特殊定制需求时配置，完全可选）

我们将其分为 **后缀配置** 与 **全包名限定配置**（全包名限定配置具有更高优先级）。

#### A. 后缀配置（默认推荐）

在不提供全包名配置时，EasyTrans 会在 **实体类所在的当前包** 后追加指定的后缀来生成代码，无需手动指定冗长的全包名，非常适合多模块或大项目。

* **`easytrans.generated.package.suffix`**: 注册表所在的包后缀（默认为 `generated.registry`）。生成后的全包名为
  `[实体类所在的包].generated.registry`。
* **`easytrans.generated.bridge.package.suffix`**: Bridge 所在的包后缀（默认为 `generated`）。生成后的全包名为
  `[实体类所在的包].generated`。

#### B. 全包名限定配置（高优先级）

如果你希望将所有生成的代码统一归集到全局某一个包下，你可以配置固定的全包名（配置后会覆盖后缀配置）：

* **`easytrans.generated.package`**: 自定义主注册表（`GeneratedTranslationRegistry`）所在的**固定全包名**。
* **`easytrans.generated.bridge.package`**: 自定义 Bridge 所在的**固定全包名**。

---

### 2. 支持非 Spring 环境（轻量级无依赖运行）

EasyTrans 默认会往生成的 Bridge 和 Registry 类上加上 `@Component`，以便在 Spring 环境中自动组装。

如果你的项目是**非 Spring 架构**（如纯 Java、Vert.x、Micronaut、Quarkus 或轻量级 Dubbo 消费者），你只需要配置 *
*`easytrans.enable.spring=false`**。

#### 开启非 Spring 环境下的代码生成变化：

* **Bridge 桥接器**：去除 `@Component`，变为普通的 POJO 类。
* **Registry 注册表**：去除 `@Component`，并在内部采用**直接 new 实例**的形式完成对全部 Bridge 的实例化。你只需在主类中直接
  `new GeneratedTranslationRegistry()` 即可零配置极速使用！

---

### Maven 配置示例：

#### A. Spring Boot 3 & 4 (JDK 17+)

直接在你的主应用中引入 `easytrans-spring-boot-starter`：

```xml
<dependency>
    <groupId>io.github.davidricardo1026</groupId>
    <artifactId>easytrans-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### B. Spring Boot 2 (JDK 17+)

直接在你的主应用中引入 `easytrans-spring-boot2-starter`：

```xml
<dependency>
    <groupId>io.github.davidricardo1026</groupId>
    <artifactId>easytrans-spring-boot2-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### APT 编译器配置（可选，通常无需配置）：

在编译插件中配置 `easytrans-processor` 注解处理器。**再次提示：编译器参数（`compilerArgs`）只有在你有特殊的自定义包名或非
Spring 架构等高级定制需求时才需要配置，常规项目直接引入上面的 Starter 依赖即可！**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <compilerArgs>
          <!-- 仅在有自定义需求时配置以下 compilerArgs： -->
          <!--
          <arg>-Aeasytrans.generated.package.suffix=customgen.registry</arg>
          <arg>-Aeasytrans.generated.bridge.package.suffix=customgen.bridge</arg>
          -->
            
            <!-- 场景 B：支持非 Spring 环境（关闭 Spring 组件注解，不加则默认为 true） -->
            <!-- <arg>-Aeasytrans.enable.spring=false</arg> -->
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>io.github.davidricardo1026</groupId>
                <artifactId>easytrans-processor</artifactId>
                <version>1.0.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

#### Gradle 配置示例 (Groovy DSL)：

对于使用 Gradle 构建的项目，可以在 `build.gradle` 中非常简便地声明依赖与编译器参数。**因为 2.0 就地翻译方案已经不需要任何
MapStruct，所以依赖声明极其轻量精简！**

```groovy
dependencies {
  // 1. 引入 EasyTrans Starter (根据你的 Spring Boot 版本选择)
  // Spring Boot 3/4 选择:
  implementation 'io.github.davidricardo1026:easytrans-spring-boot-starter:1.0.0'
  // Spring Boot 2 选择:
  // implementation 'io.github.davidricardo1026:easytrans-spring-boot2-starter:1.0.0'

  compileOnly 'org.projectlombok:lombok:1.18.30'

  // 2. 声明注解处理器 (APT)
  annotationProcessor 'org.projectlombok:lombok:1.18.30'
  annotationProcessor 'io.github.davidricardo1026:easytrans-processor:1.0.0'
}

// 3. 传入编译配置参数（可选：仅在有特殊自定义包名或非 Spring 环境等高级定制需求时配置，常规项目直接注释或省略此 block 即可）
compileJava {
  options.compilerArgs += [
          // 仅在有特殊定制需求时解除注释配置：
          // '-Aeasytrans.generated.package.suffix=customgen.registry',
          // '-Aeasytrans.generated.bridge.package.suffix=customgen.bridge',
          // '-Aeasytrans.enable.spring=false',
          // '-Aeasytrans.generated.package=io.github.easytrans.test.both.registry',
          // '-Aeasytrans.generated.bridge.package=io.github.easytrans.test.both.bridge'
  ]
}
```

---

## 💻 极简使用示例

### 1. 定义实体类 (直接在实体类上使用 `@Translatable` 和 `@TranslateField`)

```java
@Data
@Translatable
public class OrderPO {
    private Long id;
    private Long userId; // 源关联 ID 属性

    // 声明 userName 存放翻译文本，数据源来源于 userId，转义业务标识为 USER_SERVICE
    @TranslateField(source = "userId", type = "USER_SERVICE")
    private String userName; 
    
    // 100% 自动就地支持嵌套列表、嵌套集合、嵌套 Map 或多维 Map 复合体的无限层级递归就地填充！
    // 只要关联的实体类（如 OrderItemPO）上也标注了 @Translatable 即可！
    private List<OrderItemPO> items;
    private Set<OrderItemPO> itemSet;
    private Map<String, OrderItemPO> itemMap;
    private Map<String, Map<String, List<OrderItemPO>>> deepNestedItemMap;
}
```

### 2. 实现数据加载器 (实现 `TranslationFeeder` SPI)

```java
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
        // 批量 In 查询，返回 Map<ID, 展示名称>
        List<UserPO> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(UserPO::getId, UserPO::getName));
    }
}
```

### 3. 100% 零侵入 Controller

配合 starter 中集成的自动 Advice 切面，Controller 层的业务代码**极其干净，100% 零侵入**，且接口声明与实际类型 100% 契约一致！

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * Controller 声明返回 List<OrderPO> 形式。
     * 切面拦截后自动在底层：就地递归提取 ID -> Feeder 批量拉取数据 -> 极速就地回填填充，
     * 最终输出完美的已转义 OrderPO 列表。Controller 业务层 100% 干净，API 契约高度严谨！
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
  [Controller 返回实体列表]
             |
             v (ResponseBodyAdvice 拦截)
  1. ID 收集阶段 (Bridge.extractAllIds) -----------------> 编译期硬编码递归、极速收集嵌套关联 ID
                                                                       |
                                                                       v
  2. 批量加载阶段 (Feeder.batchLoad) <------------------ 聚合 ID 去重分类，一次性分发批量 IN 查询
             |
             v (填充上下文)
  3. 属性就地回填阶段 (Bridge.writeBack) <-------------- 零反射、纯硬编码 setter 极速回填数据
             |
             v
  [输出就地翻译填充后的实体列表]
```

---

## 📂 多模块 Demo 项目结构说明

我们在 `easytrans-demo` 目录下构建了一个**多模块项目**，全方位展示不同底层架构环境下的适用性：

```text
easytrans-demo
├── easytrans-demo-spring   # Spring Web MVC 环境：整合 MyBatis-Plus。BodyAdvice 零侵入就地翻译。
├── easytrans-demo-main     # 纯 Java Main 环境：演示在没有任何 Spring 容器的情况下，手动驱动就地翻译的极简流程。
├── easytrans-demo-suffix   # 测试模块：配置包后缀 (-Aeasytrans.generated.package.suffix)。
├── easytrans-demo-full     # 测试模块：配置全局唯一的固定全包名 (-Aeasytrans.generated.package)。
└── easytrans-demo-both     # 测试模块：同时配置后缀与全包名，测试全包名限定的高优先级覆盖规则。
```

---

## 📄 开源许可证 (License)

EasyTrans 采用 [Apache License 2.0](LICENSE) 许可协议，欢迎贡献代码与提报 Issue！
