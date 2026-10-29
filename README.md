# hotelRpDto-solr-sysc

基于 Spring Boot + SolrJ 8.0.0 的酒店 Solr 数据管理服务，提供酒店文档的新增、读取、更新、删除、批量操作和条件分页查询，中文检索使用 IK 分词器。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| JDK | 8 |
| Gradle | 7.6.3（项目自带 wrapper） |
| Spring Boot | 2.7.18 |
| Solr 客户端 | solr-solrj 8.0.0 |
| Solr 服务端 | 8.0.0（standalone） |
| 中文分词 | IK Analyzer 8.0.0（`com.github.magese:ik-analyzer`） |
| 接口文档 | springdoc-openapi 1.7.0（Swagger UI） |
| 测试 | JUnit 5 + Mockito + MockMvc |

## 目录结构

```
.
├── build.gradle
├── settings.gradle                 # 阿里云 / 腾讯云 / 本地仓库配置
├── gradle/wrapper                  # Wrapper 使用腾讯云镜像
├── src/main/resources/
│   └── dih-hotel-rp.json           # 只读基础数据源（classpath，不在代码中修改）
├── solr/
│   ├── schema.json                 # 业务字段的 Schema API 定义（参考）
│   └── hotelRpDto/                      # 可直接部署的 Solr core
│       ├── core.properties
│       └── conf/
│           ├── managed-schema      # 由 schema.json 生成的字段与 IK 类型
│           └── solrconfig.xml      # 基于 Solr 8.0.0 _default 调整
├── scripts/
│   ├── install-ik.sh               # 下载 IK 分词器到 Solr
│   ├── deploy-core.sh              # 部署 solr/hotelRpDto 到 Solr 安装目录
│   └── init-solr.sh                # 备选：为 _default core 幂等补字段
└── src/main/java/com/hotel/solr/
    ├── config/                     # SolrClient、配置属性、OpenAPI
    ├── controller/                 # REST 接口
    ├── dto/                        # 请求/响应模型
    ├── exception/                  # 统一异常处理
    └── service/                    # 业务逻辑、文档转换、查询构建
```

## 环境要求

- JDK 8（`java -version` 确认为 1.8）
- 可访问 Maven 仓库（已配置 mavenLocal、阿里云、腾讯云镜像）
- 已安装并启动 Solr 8.0.0 standalone

## 一、准备 Solr

### 1. 启动 Solr

```bash
cd /path/to/solr-8.0.0
bin/solr start -p 8983
```

### 2. 安装 IK 分词器

IK 是 **Solr 服务端插件**，不需要加入 Spring Boot 依赖：

```bash
SOLR_INSTALL_DIR=/path/to/solr-8.0.0 ./scripts/install-ik.sh
/path/to/solr-8.0.0/bin/solr restart
```

脚本会按 阿里云 → 腾讯云 → Maven Central 的顺序下载 `ik-analyzer-8.0.0.jar`，默认放到
`$SOLR_INSTALL_DIR/server/solr-webapp/webapp/WEB-INF/lib/`。如果 Solr 以其他方式部署，可用
`IK_JAR_DIR` 指定 jar 目录：

```bash
SOLR_INSTALL_DIR=/path/to/solr-8.0.0 IK_JAR_DIR=/your/solr/lib ./scripts/install-ik.sh
```

> 生成的 `managed-schema` 在 core 加载时就会实例化 `IKTokenizerFactory`，因此必须先安装 IK jar
> 并重启 Solr，否则 core 会加载失败。旧用法 `SOLR_HOME=/path/to/solr-8.0.0` 仍然兼容。

### 3. 部署 core（推荐）

项目已提供完整的 core 配置：`solr/hotelRpDto/conf/managed-schema` 和 `solr/hotelRpDto/conf/solrconfig.xml`。

```bash
SOLR_INSTALL_DIR=/path/to/solr-8.0.0 ./scripts/deploy-core.sh
/path/to/solr-8.0.0/bin/solr restart
```

脚本会把 `solr/hotelRpDto/` 复制到 `$SOLR_INSTALL_DIR/server/solr/hotelRpDto/`：

- 覆盖 `conf/`，保留已有 `data/` 和 `core.properties`；
- 目标配置与项目不同时，先备份为 `conf.bak.<timestamp>`；
- 默认关闭 schemaless（`update.autoCreateFields=false`），应用写入不会自动新增字段；
- 使用 `ManagedIndexSchemaFactory`，可以通过 Schema API 查看字段；字段变更请修改
  `managed-schema` 后重启 Solr。

如果已经有使用 `_default` configset 的 core，也可以继续用 Schema API 方式补齐字段：

```bash
SOLR_URL=http://localhost:8983/solr SOLR_CORE=hotelRpDto ./scripts/init-solr.sh
```

`init-solr.sh` 是幂等的，可重复执行；它会创建 core（`configSet=_default`）并逐个补齐字段。
两种方式二选一即可，推荐使用生成的 core 配置。

### 4. 验证

```bash
# core 应处于 loaded 状态
curl 'http://localhost:8983/solr/admin/cores?action=STATUS&core=hotelRpDto&wt=json'

# 应返回 text_ik 字段类型定义
curl http://localhost:8983/solr/hotelRpDto/schema/fieldtypes/text_ik

# 应包含 id、name、address、city、longitude、latitude、star、price、tags 等字段
curl http://localhost:8983/solr/hotelRpDto/schema/fields

# 中文分词效果（需已安装真实 IK jar）
curl 'http://localhost:8983/solr/hotelRpDto/analysis/field?analysis.fieldtype=text_ik&analysis.fieldvalue=上海外滩华尔道夫酒店&wt=json'
```

### 生成的配置说明

- `managed-schema`：业务字段来自 `solr/schema.json`；`id` 为唯一键且必填；`name`/`address`
  使用 `text_ik`；`rate_plan_prices`/`rate_plan_room_sts` 只存储不索引；额外包含 Solr 内部字段
  `_version_`、`_root_`、`_text_`（`name`/`address` 会复制到 `_text_`，供未指定字段的查询使用）。
- `solrconfig.xml`：基于 Solr 8.0.0 官方 `_default` configset，显式声明
  `ManagedIndexSchemaFactory`，关闭 schemaless 自动加字段，并保留 `/select`、`/update`、
  `/analysis/*`、`/admin/ping` 等常用 handler。

## 二、配置

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080

solr:
  base-url: http://localhost:8983/solr   # 后续按实际环境修改
  core: hotelRpDto
  connection-timeout-ms: 5000
  socket-timeout-ms: 15000
  commit-after-write: true               # 写后立即 commit，保证读己之写
```

应用不会在启动时自动修改 Solr 结构；core/schema 由生成的 configset（`scripts/deploy-core.sh`）或备选的 `scripts/init-solr.sh` 初始化。

## 三、构建与运行

```bash
./gradlew clean build        # 编译 + 运行测试 + 打 jar
./gradlew test               # 只运行测试
./gradlew bootRun            # 本地启动
java -jar build/libs/hotelRpDto-solr-sysc-0.0.1-SNAPSHOT.jar
```

Swagger UI：<http://localhost:8080/swagger-ui.html>

## 四、数据模型

| 字段 | Solr 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 唯一键，必填（`managed-schema` 定义） |
| `name` | text_ik | 酒店名称，IK 分词，检索权重最高 |
| `address` | text_ik | 地址，IK 分词 |
| `city_id` | string | 城市编码，精确筛选 |
| `city_name` | string | 城市名称，精确筛选 |
| `longitude` | pdouble | 经度，范围 [-180, 180]，小数点后至少 6 位 |
| `latitude` | pdouble | 纬度，范围 [-90, 90]，小数点后至少 6 位 |
| `star` | pint | 星级 |
| `price` | pfloat | 价格 |
| `tags` | string（多值） | 标签，精确筛选 |
| `rate_plan_prices` | string | 价格计划，`RpByteUtils` 编码字符串，仅存储不索引 |
| `rate_plan_room_sts` | string | 房型房态，`RpByteUtils` 编码字符串，仅存储不索引 |
| `create_time` / `update_time` | pdate | 服务端维护 |

> `_version_`、`_root_`、`_text_` 是 Solr 内部字段：`_version_` 用于原子更新/实时获取，
> `_text_` 汇总 `name` 和 `address`，供未指定字段的查询使用；业务接口不会返回这些字段。

接口 JSON 统一使用 **camelCase**，与 Java 字段名一致；Solr 内部字段名仍为 snake_case，由转换器负责映射。

经纬度使用 `BigDecimal` 接收，以保留小数位数：

- `longitude` 和 `latitude` 必须同时提供或同时省略；
- 经度范围 `[-180, 180]`，纬度范围 `[-90, 90]`；
- 小数点后至少 6 位，`121.473700` 这类末尾为 0 的值同样合法；
- 从 Solr 读取时会补齐到至少 6 位小数，保证响应格式一致。

JSON 中请直接使用数字（不要加引号），例如 `"longitude": 121.473701`。

### 基础数据读取与 HotelDo 转换

`src/main/resources/dih-hotel-rp.json` 是只读数据源，classpath 根目录下的文件名为 `dih-hotel-rp.json`。根结构为 `{"RECORDS":[...]}`，当前包含 17 条酒店记录、每条 53 个字段。读取代码只使用输入流，不会回写或修改该文件；打成 jar 后也能从 classpath 直接读取。

建模类：

| 类 | 说明 |
| --- | --- |
| `HotelInfoDataJsonSet` | 根结构，对应 `RECORDS` |
| `HotelBaseRecord` | 单条源记录，完整保留 53 个原始字段（全部按字符串接收） |
| `RatePlanData` | `rp_room_sts` / `rp_prices` 的 `date + list` 结构 |
| `HotelBaseDataLoader` | 读取、解析并转换为 `HotelDo`，由 Spring 管理 |

使用方式：

```java
@Autowired
private HotelBaseDataLoader hotelBaseDataLoader;

// 读取 classpath 根目录 dih-hotel-rp.json 并转换
List<HotelDo> hotels = hotelBaseDataLoader.loadDefault();
```

也可以指定 classpath 资源、文件路径或输入流：

```java
List<HotelDo> hotels = hotelBaseDataLoader.loadClasspath("dih-hotel-rp.json");
List<HotelDo> hotels = hotelBaseDataLoader.load(Paths.get("/data/dih-hotel-rp.json"));
List<HotelBaseRecord> records = hotelBaseDataLoader.read(Paths.get("/data/dih-hotel-rp.json"));
```

字段映射：

| 源字段 | HotelDo 字段 | 规则 |
| --- | --- | --- |
| `id` | `id` | 直接映射 |
| `hotel_name` | `name` | 直接映射 |
| `address` | `address` | 直接映射 |
| `city_id` | `cityId` | 直接映射 |
| `city_name` | `cityName` | 直接映射 |
| `google_lat` / `google_lon` | `latitude` / `longitude` | 优先使用；整对无效时回退 Baidu |
| `baidu_lat` / `baidu_lon` | `latitude` / `longitude` | Google 坐标无效时使用 |
| `star_rate` | `star` | 转为 `Integer` |
| `rp_prices` | `rate_plan_prices` | 用 `RpByteUtils` 将日期和价格列表编码为紧凑字符串 |
| `rp_room_sts` | `rate_plan_room_sts` | 用 `RpByteUtils` 将日期和房态列表编码为紧凑字符串 |

- 两对坐标都无效（含源数据的 `-1.000000000` 缺失标记）时，经纬度留空。
- 价格/房态数据缺失时，`rate_plan_prices` / `rate_plan_room_sts` 留空。
- `price`、`tags`、`create_time`、`update_time` 在源数据中没有对应值，转换时留空；写入 Solr 时由 `HotelService` 维护时间字段。
- 其余源字段保留在 `HotelBaseRecord` 中，不进入 `HotelDo`。
- 转换只发生在内存中，不会自动写入 Solr；如需导入，可在 core 部署后调用 `hotelService.saveBatch(hotels)`。

## 五、REST API

统一响应格式：

```json
{ "code": 0, "message": "ok", "data": {} }
```

`code=0` 表示成功；失败时 HTTP 状态码与 `code` 一致（400/404/502 等）。

## 初始化数据/监控检查
```bash
curl -X "POST" 'http://127.0.0.1:9010/api/solr/health' -H 'Content-Type: application/json' 

curl -X "POST" 'http://127.0.0.1:9010/api/solr/import' -H 'Content-Type: application/json' 
```

## 详情数据
```bash
curl -X "GET" 'http://127.0.0.1:9010/api/hotels/53876' -H 'Content-Type: application/json' 
```

### 查询
```bash



## 城市 + 日期范围
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-27","checkout":"2026-10-28"}'
  
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-27","checkout":"2026-10-30"}'

## 城市 + 日期范围 + 关键字
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-27","checkout":"2026-10-28", "keyword":"商务"}'
  
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-28","checkout":"2026-10-29", "keyword":"商务"}'

## 城市 + 日期范围 + 价格范围 （房价过滤） + 房态(硬性要求：房态过滤)
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-27","checkout":"2026-10-28", "priceFrom":100, "priceTo":200}'

curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-28","checkout":"2026-10-29", "priceFrom":100, "priceTo":200}'


curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-10-28","checkout":"2026-10-29", "priceFrom":100, "priceTo":200}'  

### 无房价 + 有房态
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-10-30","checkout":"2026-10-31", "priceFrom":100, "priceTo":200}'  

### 有房价 + 有房态
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-10-30","checkout":"2026-10-31", "priceFrom":100, "priceTo":600}'  

### 有房价 + 房态为 0
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-10-30","checkout":"2026-11-01", "priceFrom":100, "priceTo":600}'  

### 连续多天 有房价 + 房态 大于0 
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-11-01","checkout":"2026-11-04", "priceFrom":100, "priceTo":600}'  

curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801", "hotelId":"21801032","checkin":"2026-11-01","checkout":"2026-11-04" }'  

fq={!frange l=100 u=200}rp_prices(rate_plan_prices, 20753, 1, 100, 200)
dq={!frange l=100 u=500}rp_prices(rate_plan_prices, 20753, 2, 100, 500)
  
curl -X "POST" 'http://127.0.0.1:9010/api/hotels/query' -H 'Content-Type: application/json' \
  -d '{"cityId":"1801","checkin":"2026-10-30","checkout":"2026-11-03", "priceFrom":10, "priceTo":100}'
```

### 条件分页查询

```bash
curl -G http://localhost:8080/api/hotelRpDtos \
  --data-urlencode 'keyword=外滩' \
  --data-urlencode 'city=上海' \
  --data-urlencode 'star=5' \
  --data-urlencode 'min_price=1000' \
  --data-urlencode 'max_price=5000' \
  --data-urlencode 'tags=江景' \
  --data-urlencode 'sort=price desc' \
  --data-urlencode 'page=1' \
  --data-urlencode 'size=10'
```

参数说明：

| 参数 | 说明 |
| --- | --- |
| `keyword` | 对 `name^2`、`address` 做 edismax 检索，空格分隔多个词 |
| `city` | 城市名称精确匹配（`city_name`） |
| `star` | 星级精确匹配 |
| `min_price` / `max_price` | 价格区间（可只传一端） |
| `tags` | 标签，可重复传参，多个标签为 OR 关系 |
| `sort` | `score`、`price`、`star`、`create_time`、`update_time`，方向 `asc`/`desc`，默认 `score desc` |
| `page` / `size` | 页码从 1 开始，`size` 最大 200 |

### 批量操作

```bash
curl -X POST http://localhost:8080/api/hotelRpDtos/batch \
  -H 'Content-Type: application/json' \
  -d '{"hotels":[
    {"id":"h-1002","name":"上海和平饭店","city":"上海","star":5,"price":1888},
    {"id":"h-1003","name":"北京王府井酒店","city":"北京","star":5,"price":1588}
  ]}'

curl -X POST http://localhost:8080/api/hotelRpDtos/batch-delete \
  -H 'Content-Type: application/json' \
  -d '{"ids":["h-1002","h-1003"]}'
```

### Solr 运维

```bash
# 连接与 core 状态；DOWN 时返回 HTTP 503
curl http://localhost:8080/api/solr/health

# 手动 commit
curl -X POST http://localhost:8080/api/solr/commit
```

## 六、测试

```bash
./gradlew test
```

测试不依赖真实 Solr 实例：Service 层使用 Mockito 模拟 `SolrClient`，Controller 使用 MockMvc。
`HotelBaseDataLoaderTest` 使用 `@SpringBootTest` + JUnit 5 `@ExtendWith(SpringExtension.class)`，
通过 `@Autowired` 注入 `HotelBaseDataLoader`，验证真实上下文下从 classpath 读取、解析与转换。

## 七、仓库与构建说明

`settings.gradle` 与 `build.gradle` 中的仓库顺序：

1. `mavenLocal()`（`~/.m2/repository`）
2. 阿里云 `https://maven.aliyun.com/repository/public`（插件使用 `gradle-plugin`）
3. 腾讯云 `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`
4. Maven Central 兜底

Gradle Wrapper 的发行包地址已切换为腾讯云镜像。

## 查询样例
df=rp_prices(rate_plan_prices, 20753, 2, 10, 300)
fq={!frange l=10}myCustomLen(title)
fq={!frange l=10 u=300}rp_prices(rate_plan_prices, 20753, 2, 10, 300)
fq={!frange l=200 u=300}rp_prices(rate_plan_prices, 20754, 2, 200, 300)
fq={!frange l=10 u=50}rp_prices(rate_plan_prices, 20753, 2, 10, 50)
fq={!frange l=10 u=50}rp_prices(rate_plan_prices, 20753, 5, 10, 50)
fq={!frange l=10 u=50}rp_prices(rate_plan_prices, 20753, 10, 10, 50)
## 九、常见问题

| 现象 | 处理 |
| --- | --- |
| core 加载失败或初始化 schema 报 `IKTokenizerFactory` 找不到 | 先执行 `install-ik.sh` 并重启 Solr；生成的 `managed-schema` 在加载时就需要 IK 类 |
| 部署新配置后 core 加载失败 | 查看 Solr 日志，确认 IK jar 已安装、`server/solr/hotelRpDto/conf` 文件完整；必要时用 `conf.bak.*` 回滚 |
| 请求返回 502，提示连接失败 | 检查 `solr.base-url`、Solr 是否启动、core 是否存在 |
| 中文搜索召回差 | 确认 `name`/`address` 使用 `text_ik`，并检查 `/analysis/field` 分词结果 |
| 修改字段后旧数据查不到 | 需要重建索引；`deploy-core.sh` 不会删除已有 `data/` |
| 写入后立即查不到 | 保持 `commit-after-write: true`，或等待 Solr 自身 autoCommit |
| 部分更新报文档不存在 | PATCH 前会先校验文档；确认 id 正确 |
| 经纬度返回 400 | 确认经纬度成对提供、范围正确，且 JSON 小数位至少 6 位（如 `121.473701`） |
| 提示 `读取酒店基础数据失败: classpath:dih-hotel-rp.json` | 确认 `src/main/resources/dih-hotel-rp.json` 存在，并已包含在构建产物中 |
