# hotelRpDto-solr-sysc

基于 Spring Boot 2.7 + SolrJ 8.0.0 的酒店 / 价格计划 Solr 数据服务。应用同时访问两个 Solr core：

- `hotel_rp`：酒店维度主数据，对应 `/api/hotels/**`；
- `hotel_rps_core`：价格计划明细，对应 `/api/rp/**`，分页查询时按 `hotel_id` 分组。

中文检索使用 IK 分词器；价格、房态的区间过滤与排序依赖独立仓库提供的 Solr 自定义
`ValueSource` 插件（`fq_rp_prices` / `fq_rp_sts`）。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| JDK | 8 |
| Gradle | 7.6.3（项目自带 wrapper） |
| Spring Boot | 2.7.18 |
| Solr 客户端 | solr-solrj 8.0.0 |
| Solr 服务端 | 8.0.0（standalone） |
| 中文分词 | IK Analyzer 8.0.0（`com.github.magese:ik-analyzer`） |
| 自定义函数 | 独立仓库 `solr-extend-rate-plan`，见第三节 |
| 接口文档 | springdoc-openapi 1.7.0（Swagger UI） |
| 测试 | JUnit 5 + Mockito + MockMvc |

## 目录结构

```text
.
├── build.gradle
├── settings.gradle                         # 阿里云 / 腾讯云 / 本地仓库配置
├── gradle/wrapper                          # Wrapper 使用腾讯云镜像
├── src/main/resources/
│   ├── application.yml                     # 端口、Solr 地址、提交策略
│   ├── hotel-rp-set.json                   # 酒店主数据（17 条）
│   └── rate-plan-set.json                  # 价格计划数据（9 条）
├── src/main/java/com/hotel/solr/
│   ├── config/                             # SolrClient、SolrProperties、OpenAPI
│   ├── controller/                         # /api/hotels、/api/rp、/api/solr
│   ├── framework/                          # 统一响应、分页、异常处理、RpByteUtils
│   ├── model/                              # PO / DTO / BO / 请求模型 / 排序枚举
│   ├── service/                            # AbstractSolrService 及两个 core 的实现
│   └── util/DataLoader.java                # classpath 数据读取与转换
├── solr/
│   ├── schema.json                         # 早期参考（字段已过期，见第七节）
│   └── hotel/conf/
│       ├── managed-schema                  # 可部署的字段定义与 text_ik 类型
│       └── solrconfig.xml                  # 基于 Solr 8.0.0 _default 调整
└── scripts/
    ├── deploy-core.sh                      # 单 core 配置部署（默认 core=hotel）
    └── init-solr.sh                        # Schema API 字段初始化（默认 hotel_rp）
```

## 环境要求

- JDK 8（`java -version` 确认为 1.8）；
- Solr 8.0.0，standalone 模式；
- 可访问 Maven 仓库（项目已配置 mavenLocal、阿里云、腾讯云镜像）。

### 环境变量约定

下文命令统一使用以下变量，请按实际环境设置：

| 变量 | 说明 |
| --- | --- |
| `SOLR_HOME` | Solr 8.0.0 安装目录 |
| `SOLR_URL` | Solr 访问地址，格式为 `http://<solr-host>:<port>/solr` |
| `APP_URL` | 应用访问地址，格式为 `http://<app-host>:<port>` |

```bash
# 以下为示例值，请按实际环境替换
export SOLR_HOME=/opt/solr-8.0.0
export SOLR_URL=http://solr.example.com:8983/solr
export APP_URL=http://app.example.com:9010
```

## 一、安装 Solr

```bash
# 1. 下载并解压（也可使用已有的 Solr 8.0.0 安装包）
wget https://archive.apache.org/dist/lucene/solr/8.0.0/solr-8.0.0.tgz
tar -xzf solr-8.0.0.tgz
export SOLR_HOME=/opt/solr-8.0.0   # 按实际安装目录修改

# 2. 启动（端口按实际环境调整）
$SOLR_HOME/bin/solr start -p 8983

# 3. 确认进程与端口
$SOLR_HOME/bin/solr status
curl -s "$SOLR_URL/admin/info/system?wt=json" | head -c 200
```

Solr 8.0.0 要求 Java 8 及以上；如果机器上有多个 JDK，请确认 `JAVA_HOME` 指向 JDK 8。

## 二、安装 IK 分词器

`managed-schema` 中的 `text_ik` 字段类型直接引用了
`org.wltea.analyzer.lucene.IKTokenizerFactory`，**必须先安装 IK jar 再创建 / 加载 core**，
否则 core 会因找不到分词器类而加载失败。

```bash
# 1. 下载 IK Analyzer 8.0.0
curl -fL -o ik-analyzer-8.0.0.jar \
  https://repo1.maven.org/maven2/com/github/magese/ik-analyzer/8.0.0/ik-analyzer-8.0.0.jar

# 2. 放入 Solr Web 应用类路径
cp ik-analyzer-8.0.0.jar \
  "$SOLR_HOME/server/solr-webapp/webapp/WEB-INF/lib/"

# 3. 重启 Solr（jar 不会热加载）
$SOLR_HOME/bin/solr restart
```

验证分词效果（需 core 已创建）：

```bash
curl -s -G "$SOLR_URL/hotel_rp/analysis/field" \
  --data-urlencode 'analysis.fieldtype=text_ik' \
  --data-urlencode 'analysis.fieldvalue=上海外滩华尔道夫酒店' \
  --data-urlencode 'wt=json' | jq .
```

如果无法访问 Maven Central，可替换为阿里云 / 腾讯云镜像地址，或从本地 Maven 仓库
`com/github/magese/ik-analyzer/8.0.0/` 目录直接复制。

## 三、部署自定义解析组件（Solr ValueSource 插件）

### 3.1 组件说明

酒店价格与房态以 `RpByteUtils` 编码后的紧凑字符串存放在 `rate_plan_prices` /
`rate_plan_room_sts` 字段中，无法直接用 Solr 内置字段类型比较和排序。因此价格区间过滤、
价格排序、房态过滤都通过自定义 `ValueSourceParser` 实现：

| 函数 | 参数 | 返回值 |
| --- | --- | --- |
| `fq_rp_prices(rate_plan_prices, offset, days)` | `offset`：入住日距 `1970-01-01` 的天数；`days`：入住晚数 | 单晚返回该晚价格；多晚返回各晚价格平均值（整数除法）；任一晚为 0、数据缺失或入住区间超出计划返回 `0`；任一晚为负返回 `-1` |
| `fq_rp_sts(rate_plan_room_sts, offset, days)` | 同上 | 返回各晚房态的最小值；任一晚为 0、数据缺失或超出计划返回 `0`；任一晚为负返回 `-2` |

价格过滤由外层 `{!frange l=min u=max}` 完成，房态过滤使用 `{!frange l=1}`。
插件源码不在本仓库，位于独立工程 `solr-extend-rate-plan`，包名为 `com.up.solr.function`：

- `RpPricesValueSourceParser` / `RpPricesValueSource`；
- `RpRoomStsValueSourceParser` / `RpRoomStsValueSource`；
- `RpByteUtils`（与业务仓库中的编码工具保持一致的字节结构）。

### 3.2 构建插件 jar

参考源码地址 https://github.com/gaoshanwenfeng/solr-extend-rate-plan

在 `solr-extend-rate-plan` 仓库根目录执行：

```bash
./gradlew clean build

# 产物
ls build/libs/solr-extend-rate-plan-*.jar
```

### 3.3 部署 jar

把 jar 复制到**每个需要该函数的 core** 的 `lib/` 目录（Solr 8 会自动加载
`instanceDir/lib`）：

```bash
for core in hotel_rp hotel_rps_core; do
  mkdir -p "$SOLR_HOME/server/solr/$core/lib"
  cp build/libs/solr-extend-rate-plan-*.jar \
    "$SOLR_HOME/server/solr/$core/lib/"
done
```

如果需要放在共享目录（例如 `contrib/user-ext-lib`），要在 `solrconfig.xml` 中显式声明：

```xml
<lib dir="${solr.install.dir:../../../..}/contrib/user-ext-lib" regex="solr-.*\.jar"/>
```

> 同一目录只保留一个版本的可执行 jar。多个版本会同时参与类加载，实际生效的版本不确定；
> 升级时把旧 jar 改名（如加 `.bak` 后缀）移出 `*.jar` 匹配范围。

### 3.4 注册函数

在 `hotel_rp` 和 `hotel_rps_core` 两个 core 的 `conf/solrconfig.xml` 末尾
（`</config>` 之前）加入：

```xml
<valueSourceParser name="fq_rp_prices"
    class="com.up.solr.function.RpPricesValueSourceParser"/>
<valueSourceParser name="fq_rp_sts"
    class="com.up.solr.function.RpRoomStsValueSourceParser"/>
```

**函数名必须与应用程序使用的 `fq_rp_prices` / `fq_rp_sts` 完全一致。** 插件仓库 README
中的示例注册名是 `rp_prices` / `rp_room_sts`，直接复制会与应用不匹配。

> 本仓库的 `solr/hotel/conf/solrconfig.xml` 没有这两个注册项（见第七节），部署配置后需要
> 手工补上。修改 `solrconfig.xml` 后可执行 RELOAD；新增 jar 后必须重启 Solr。

### 3.5 验证

```bash
# 查看函数是否注册
curl -s "$SOLR_URL/hotel_rp/config?wt=json" \
  | jq '.config.valueSourceParser'

# 直接计算某条文档的价格（20758 = 2026-11-01，1 晚）
curl -s -G "$SOLR_URL/hotel_rp/select" \
  --data-urlencode 'q=*:*' \
  --data-urlencode 'rows=1' \
  --data-urlencode 'fl=hotel_id,rate_plan_prices,fq_rp_prices(rate_plan_prices,20758,1)' \
  --data-urlencode 'wt=json'
```

### 3.6 字段编码格式（RpByteUtils）

每个 unit 占 4 字节，大端序 int：

```text
bytes[0..3]   起始日距 1970-01-01 的天数偏移
bytes[4..7]   数量 N
bytes[8..11]  第 1 个数值
bytes[12..15] 第 2 个数值
...
总长度 = (2 + N) * 4 字节
```

`byte[]` 与 `String` 之间使用 ISO-8859-1 转换，保证 1 字节对应 1 字符；不要用 UTF-8
或 US-ASCII，否则二进制数据无法原样还原。`RpByteUtils` 同时校验长度与偏移对齐。

## 四、创建 Core

### 4.1 Core 规划

| core | 用途 | 数据服务 | 接口前缀 |
| --- | --- | --- | --- |
| `hotel_rp` | 酒店维度主数据 | `HotelRpService` | `/api/hotels/**` |
| `hotel_rps_core` | 价格计划明细，按 `hotel_id` 分组查询 | `RatePlanService` | `/api/rp/**` |

两个 core 使用同一份 schema（`solr/hotel/conf`），core 名称硬编码在
`SolrProperties#hotelCoreUrl()` / `ratePlanCoreUrl()` 中。

### 4.2 创建并部署配置

```bash
export SOLR_HOME=/opt/solr-8.0.0   # 按实际安装目录修改
# 在项目根目录执行

# 1. 创建两个 core（默认使用 _default configset；已存在则跳过）
$SOLR_HOME/bin/solr create -c hotel_rp
$SOLR_HOME/bin/solr create -c hotel_rps_core

# 2. 用项目配置覆盖 core 的 conf（先备份，避免丢配置）
for core in hotel_rp hotel_rps_core; do
  cp -R "$SOLR_HOME/server/solr/$core/conf" \
        "$SOLR_HOME/server/solr/$core/conf.bak.$(date +%Y%m%d%H%M%S)" 2>/dev/null || true
  cp -R solr/hotel/conf/. "$SOLR_HOME/server/solr/$core/conf/"
done

# 3. 按第 3.4 节在两个 solrconfig.xml 中注册自定义函数

# 4. 重启 Solr
$SOLR_HOME/bin/solr restart
```

说明：

- `managed-schema` 已关闭 schemaless 自动加字段（`update.autoCreateFields=false`），
  写入未定义字段会被 Solr 拒绝；
- `id` 是 `uniqueKey`；`rate_plan_id`、`hotel_id` 在 schema 中为必填字段；
- `scripts/deploy-core.sh` 只处理单个 core（`SOLR_CORE`，源目录 `solr/$CORE`，默认
  `hotel`），与当前双 core 结构不匹配，首次使用前还必须先创建 core。推荐按上面的命令
  手动复制配置；
- `scripts/init-solr.sh` 已按当前 `managed-schema` 更新业务字段模型，默认初始化 `hotel_rp`；
  初始化价格计划 core 时执行 `SOLR_CORE=hotel_rps_core ./scripts/init-solr.sh`。core 不存在
  时会用 `_default` configset 创建，旧脚本遗留的 `city` 等字段不会自动删除；脚本只通过
  Schema API 补字段，不修改 `solrconfig.xml`，自定义函数仍需按 3.4 节注册。

### 4.3 应用配置

应用配置位于 `src/main/resources/application.yml`，主要配置项如下：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 应用监听端口，按部署环境设置 |
| `solr.base-url` | Solr 服务地址（standalone），按实际环境修改 |
| `solr.connection-timeout-ms` / `solr.socket-timeout-ms` | 连接 / 读取超时（毫秒） |
| `solr.commit-after-write` | 写操作后是否立即 commit，保证读己之写 |
| `solr.core` | 可选，仅用于健康检查响应中的 `core` 字段展示，不参与实际路由 |

具体取值以 `application.yml` 为准，部署时通过环境变量或配置中心覆盖。`solr.core` 未在
yml 中显式配置时，使用 `SolrProperties` 中的默认值。

### 4.4 验证

```bash
# core 应为 loaded 状态
curl -s "$SOLR_URL/admin/cores?action=STATUS&core=hotel_rp&wt=json" | jq .
curl -s "$SOLR_URL/admin/cores?action=STATUS&core=hotel_rps_core&wt=json" | jq .

# 字段与 IK 类型
curl -s "$SOLR_URL/hotel_rp/schema/fields" | jq -r '.fields[].name'
curl -s "$SOLR_URL/hotel_rp/schema/fieldtypes/text_ik" | jq .
```

## 五、导入数据

### 5.1 数据源

| 文件 | 记录数 | 导入目标 |
| --- | --- | --- |
| `src/main/resources/hotel-rp-set.json` | 17 | `hotel_rp` |
| `src/main/resources/rate-plan-set.json` | 9 | `hotel_rps_core` |

根结构为 `{"RECORDS":[...]}`，`DataLoader` 只读取输入流，不会修改源文件。每条源记录
包含 53 个字段（`fax` 为可选字段），完整保留在 `HotelInfoRecordBo` 中，仅以下字段进入
Solr：

| 源字段 | Solr 字段 | 规则 |
| --- | --- | --- |
| `id` | `id`、`rate_plan_id` | 直接映射；`rate_plan_id` 与 `id` 相同 |
| `mhotel_id` | `hotel_id` | 直接映射 |
| `hotel_name` | `name` | 直接映射，`text_ik` |
| `address` | `address` | 直接映射，`text_ik` |
| `city_id` | `city_id` | 直接映射 |
| `city_name` | `city_name` | 直接映射 |
| `google_lat` / `google_lon` | `location` | 优先使用；整对无效时回退 Baidu |
| `baidu_lat` / `baidu_lon` | `location` | Google 坐标无效时使用 |
| `star_rate` | `star` | 转为 `Integer` |
| `rp_prices` | `rate_plan_prices` | `RpByteUtils` 编码为紧凑字符串 |
| `rp_room_sts` | `rate_plan_room_sts` | `RpByteUtils` 编码为紧凑字符串 |

- 两对坐标都无效（含 `-1.000000000` 缺失标记、越界值）时，`location` 留空；
- 价格 / 房态数据缺失时，对应编码字段留空；
- 源数据中的 `tag`、`_timestamp` 未映射；`tags` 通过接口维护，`create_time` / `update_time`
  在写入时由服务端生成。

### 5.2 导入接口

```bash
# 导入酒店主数据 -> hotel_rp
curl -X POST "$APP_URL/api/hotels/import"

# 导入价格计划数据 -> hotel_rps_core
curl -X POST "$APP_URL/api/rp/import"
```

导入逻辑写在两个 Service 的 `initCoreData()` 中：逐条调用 `create()`，相同的 `id` 会覆盖
已有文档。`commit-after-write=true` 时每条记录都会 commit，数据量大时较慢（见第七节）。

### 5.3 验证

```bash
curl -s "$APP_URL/api/solr/health" | jq .

curl -s "$SOLR_URL/hotel_rp/select?q=*:*&rows=0&wt=json" \
  | jq '.response.numFound'          # 期望 17

curl -s "$SOLR_URL/hotel_rps_core/select?q=*:*&rows=0&wt=json" \
  | jq '.response.numFound'          # 期望 9
```

## 六、接口与测试用例

### 6.1 通用响应

```json
{ "code": 0, "message": "ok", "data": {} }
```

`code=0` 表示成功；失败时 HTTP 状态码与 `code` 一致（400 / 404 / 502 等）。接口 JSON 统一
使用 camelCase，Solr 内部字段名为 snake_case，由转换器负责映射。

### 6.2 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/hotels/import` | 导入 classpath 酒店数据到 `hotel_rp` |
| POST | `/api/hotels` | 新增或覆盖酒店（201） |
| PUT | `/api/hotels/{id}` | 全量更新，不存在时创建 |
| PATCH | `/api/hotels/{id}` | 部分更新（原子更新） |
| GET | `/api/hotels/{id}` | 按 `id` 读取 |
| DELETE | `/api/hotels/{id}` | 按 `id` 删除 |
| POST | `/api/hotels/query` | 分页条件查询（酒店文档） |
| POST | `/api/hotels/batch` | 批量新增 / 覆盖 |
| POST | `/api/hotels/batch-delete` | 批量删除 |
| POST | `/api/rp/import` | 导入 classpath 价格计划数据到 `hotel_rps_core` |
| POST | `/api/rp/query` | 分页条件查询，按 `hotel_id` 分组 |
| GET | `/api/rp/{id}` | 按 `id` 读取价格计划文档 |
| GET | `/api/solr/health` | Solr 连接与 `hotel_rp` 状态；DOWN 返回 503 |
| POST | `/api/solr/commit` | 手动 commit |

### 6.3 查询参数与排序

| 参数 | 说明 |
| --- | --- |
| `keyword` | 对 `name^2`、`address` 做 edismax 检索，空格分隔多个词 |
| `cityId` | 城市 ID 精确匹配（`city_id`），必填 |
| `checkin` / `checkout` | 入住、离店日期，格式 `yyyy-MM-dd`，均必填 |
| `star` | 星级精确匹配 |
| `priceFrom` / `priceTo` | 价格区间，需同时传；按入住区间价格计算 |
| `tags` | 标签，多个标签为 OR 关系 |
| `hotelId` | 酒店 ID 精确匹配（`hotel_id`） |
| `location` | 位置 `{lat, lon, distance}`；`distance` 单位公里，默认 5.0；携带后默认按距离排序，并返回 `distance` 字段 |
| `sortType` | `DISTANCE`（距离近→远）、`PRICE`（价格）、`STAR`（星级）、`SCORE`（相关度评分） |
| `sortOrder` | `ASC` / `DESC`，仅对 `PRICE` 和 `STAR` 生效 |
| `page` / `size` | 页码从 1 开始，`size` 最大 200 |

排序规则：

| sortType | sortOrder 未传 | `ASC` | `DESC` |
| --- | --- | --- | --- |
| 未指定 | 有 `location`：距离近→远；否则评分高→低 | - | - |
| `DISTANCE` | `geodist() asc` | 忽略 | 忽略 |
| `PRICE` | 价格低→高 | 价格低→高 | 价格高→低 |
| `STAR` | 星级高→低 | 星级低→高 | 星级高→低 |
| `SCORE` | 评分高→低 | 忽略 | 忽略 |

说明：

- `DISTANCE` 必须携带 `location`，否则返回 400；
- 价格排序使用 `fq_rp_prices(rate_plan_prices, offset, days) asc/desc`，同时返回
  `avgPrice`；没有价格计划或入住区间超出计划的文档函数值为 0；
- 星级排序使用 Solr 字段 `star`；
- `/api/rp/query` 使用 `group=true&group.field=hotel_id&group.ngroups=true`：
  `total` 是酒店分组数而不是文档数，`start` / `rows` 作用于分组，每组只返回排序后的
  第一条文档作为该酒店的代表记录。

响应中的主要字段：`id`、`hotelId`、`ratePlanId`、`name`、`address`、`cityId`、`cityName`、
`location`（`"纬度,经度"`）、`star`、`tags`、`priceList`、`stsList`、`distance`、`avgPrice`、
`createTime`、`updateTime`。`price` 字段目前没有赋值，价格请使用 `priceList` / `avgPrice`。

### 6.4 接口验证用例

```bash
# 1. 城市 + 日期范围
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02"}'

# 2. 关键字检索
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","keyword":"酒店"}'

# 3. 价格区间 + 房态过滤
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","priceFrom":10,"priceTo":500}'

# 4. 地理位置：默认按距离由近到远
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","location":{"lat":39.073851,"lon":117.365759,"distance":20.0}}'

# 5. 价格升序 / 降序
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","sortType":"PRICE"}'

curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","sortType":"PRICE","sortOrder":"DESC"}'

# 6. 星级高到低 / 低到高
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","sortType":"STAR"}'

curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","sortType":"STAR","sortOrder":"ASC"}'

# 7. 错误用例：距离排序但未提供 location -> 400
curl -X POST "$APP_URL/api/hotels/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-11-01","checkout":"2026-11-02","sortType":"DISTANCE"}'

# 8. 分组查询：total 为酒店分组数
curl -X POST "$APP_URL/api/rp/query" \
  -H 'Content-Type: application/json' \
  -d '{"cityId":"0301","checkin":"2026-10-27","checkout":"2026-10-28","priceFrom":10,"priceTo":500}'

# 9. 详情、批量、运维
curl -X GET "$APP_URL/api/hotels/53876"
curl -X POST "$APP_URL/api/hotels/batch" \
  -H 'Content-Type: application/json' \
  -d '{"hotels":[{"id":"h1","name":"酒店1"},{"id":"h2","name":"酒店2"}]}'
curl -X POST "$APP_URL/api/hotels/batch-delete" \
  -H 'Content-Type: application/json' \
  -d '{"ids":["h1","h2"]}'
curl -X POST "$APP_URL/api/solr/commit"
```

### 6.5 单元测试

```bash
./gradlew test          # 只运行测试
./gradlew clean build   # 编译 + 测试 + 打 jar
```

测试不依赖真实 Solr：Service 层用 Mockito 模拟 `SolrClient`，Controller 用 MockMvc。
`DataLoaderTest` 使用 `@SpringBootTest` 验证真实 Spring 上下文下的数据读取与转换。

| 测试类 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| `HotelRpDtoControllerTest` | 14 | 新增 / 更新 / 删除 / 查询参数绑定、参数校验、批量接口 |
| `SolrAdminControllerTest` | 3 | 健康检查 200/503、commit |
| `PageResultTest` | 3 | 总页数计算 |
| `HotelQueryBuilderTest` | 11 | 动态函数字段、价格 / 房态过滤、距离、`sortType` / `sortOrder` 组合、非法距离排序 |
| `DataLoaderTest` | 7 | 53 字段解析、坐标回退、价格 / 房态编码、DTO 转换 |
| `HotelRpDtoServiceTest` | 25 | CRUD、坐标校验、批量操作、commit 策略、Solr 异常转换 |
| `HotelRpDtoSolrConverterTest` | 10 | PO / DTO / SolrDocument 互转、原子更新、日期区间解析 |
| `RatePlanServiceTest` | 3 | `group=true&group.field=hotel_id`、每组取第一条、分组总数、无分组响应回退 |
| `RpBytesUtilsTest` | 1 | 实际是 DataLoader 的冒烟测试（命名问题见第七节） |
| `SolrAdminServiceTest` | 4 | 健康检查、commit 及异常转换 |

当前共 81 个测试用例，`./gradlew test` 应输出 `BUILD SUCCESSFUL`。

构建说明：

- 仓库顺序为 `mavenLocal()` → 阿里云 → 腾讯云 → Maven Central；
- SolrJ 8.0.0 的 `JavaBinCodec` 依赖 ZooKeeper 中的 `ByteBufferInputStream`，即使只用
  `HttpSolrClient` 也不能排除 `zookeeper` 传递依赖，否则解析响应时会抛
  `NoClassDefFoundError`。

## 七、遗留问题

| # | 问题 | 影响与建议                                                                                                                                   |
| --- | --- |-----------------------------------------------------------------------------------------------------------------------------------------|
| 1 | 自定义函数插件不在本仓库 | 修改插件后必须重新 `./gradlew build`、替换 jar 并重启 Solr；同名类多版本共存时生效版本不确定，升级时移出旧 jar                                                                                                                                 |
| 2 | 仓库内 `solr/hotel/conf/solrconfig.xml` 没有注册 `fq_rp_prices` / `fq_rp_sts` | 当前可用环境是手工注册的；用脚本覆盖 `conf` 后查询 / 排序会报未定义函数，部署后需按 3.4 节补注册                                                                                |
| 3 | `scripts/install-ik.sh` 已被删除，但 `deploy-core.sh` 仍引用它 | 按第二节手工下载并安装 IK；脚本提示文字会误导                                                                                                              |
| 4 | `deploy-core.sh` 与双 core 不匹配 | 脚本源目录固定为 `solr/$CORE`（默认 `solr/hotel`），且仓库内没有 `solr/hotel/core.properties`；当前应用使用 `hotel_rp` / `hotel_rps_core`，推荐手动复制 `conf`           |
| 5 | `solr/schema.json` 是早期参考 | 字段已与 `managed-schema` 不一致，以 `solr/hotel/conf/managed-schema` 为准                                                                         |
| 8 | 重复导入不会清理旧数据 | 相同 `id` 覆盖，源数据中已删除的文档会残留；需要全量重建时先清空 core（删索引后重新导入）                                                                                      |
| 9 | 分组查询语义与普通分页不同 | `/api/rp/query` 的 `total` 是酒店分组数，`start` / `rows` 作用于分组，每组只返回一条代表文档；前端不能按文档总数理解                                                         |
| 10 | 价格排序对无价格文档不友好 | 函数在数据缺失、价格为 0 或入住区间超出计划时返回 0，升序排序会把这些文档排在最前；建议同时使用价格区间过滤或由前端过滤                                                                          |
| 11 | 测试命名与自动化覆盖不足 | `RpBytesUtilsTest` 实际内容为 DataLoader 测试；没有连接真实 Solr 的自动化集成测试，当前端到端验证依赖第 6.4 节的 curl 用例 |
| 12 | 核心名称硬编码、接口无鉴权 | `hotel_rp` / `hotel_rps_core` 写死在 `SolrProperties`；接口没有认证、限流，仅适合内网或网关之后部署 |
