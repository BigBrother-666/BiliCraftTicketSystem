# BiliCraft车票系统

## 1. 简介

在 TrainCarts 车票系统的基础上开发的适用于 BiliCraft 国铁模式的车票系统。玩家可以持票/不持票上车：

- **不持票上车**：每站都停车（站站乐），bossbar 显示滚动站名。
- **持票/刷卡上车（直达车）**：根据车票记录的路线，沿跨越中间所有站直达终点。

同时生成的 BctsGuard 插件可以防止玩家使用制图台复制车票/交通卡，两个插件不可以同时安装在一个服务器，没有铁路系统的服务器只需安装 BctsGuard 插件。

## 2. 路径查找

路径查找基于**铁路遍历生成的 geojson 路由图**：

- 通过 `railgeo` 系列指令沿真实轨道遍历，把每条线路的车站（platform 控制牌）、道岔（bcswitcher 控制牌）和它们之间的区间导出为 geojson 文件（见第 9 节）。
- 插件启动/重载时由 `GeoGraphLoader` 读入所有 `*.geojson`，构建成一张**有向图**（`GeoRouteGraph`）：节点为车站/道岔，边为区间。
- 查询车票时，`GeoRouteEngine` 用 **Dijkstra 最短路径**在图上寻路。同一车站可能有多个站台节点，按起点站名枚举各站台分别寻路，返回距离升序的候选路线。
- 寻路得到的路线会推导出一条 各道岔应选方向 的导航序列（见下 直达车导航机制），写入列车属性，列车据此在每个 bcswitcher 自动选向。

### 2.1 直达车导航机制

直达车根据**导航序列**——途经各 bcswitcher 应选的方向的有序列表，存于列车属性（记录TC的ITrainProperty）：

- 购票/刷卡上车时，由寻路结果（`GeoRoutePath.switcherLineIds()`）生成导航序列写入列车。
- 列车每经过一个 bcswitcher，该道岔按导航序列**当前项**选向，随后导航指针推进一格。
- bossbar 进度 = 已过道岔数 / 道岔总数；导航走完即视为到达终点区段。

调试用 `/ticket traininfo` 查看某列车的导航序列与进度，用 `/ticket switchtrace on` 把每次道岔选向打印到控制台。

## 3. 站台与上车判断

车票记录了起点站名与起点线路ID。发车控制牌（bcspawn）会把列车的起点车站名和线路ID写入列车属性，玩家使用车票上车时据此校验是否在正确的车站。交通卡支持任意站台上车，从玩家所在站台节点重新寻路到终点。

### 3.1 票价计算

票价**按路线的每一段轨道分别计费**：每段轨道按其所属铁路系统的 `price-per-km`（`railway_system.yml` 中选填，未填则用 `config.yml` 的全局 `price-per-km`）乘以该段距离累加得到。

支付票价的玩家若是某段所属铁路系统的成员，则**免除该段票价**。

### 3.2 铁路系统收入

每个铁路系统在 `railway_system.yml` 中有三个**自动维护**字段（玩家不可在编辑向导中看到或修改）：

- `creator`：系统创建者 UUID，游戏内创建时写入，之后不可更改。
- `income`：累计收入，购票 / 刷卡时实时累加。
- `withdrawn`：已发放给创建者的金额。

玩家**实际支付**的金额按各系统的原始段费比例分摊计入各系统的累计收入。交通卡起步价（`base-fare`）整体计入行程**起点段**所属系统；刷卡者若是起点段所属系统的成员，则**免除起步价**。

每次购票 / 刷卡都把这笔分摊明细写入数据库 `transit_pass_usage_info.price` 字段。

铁路系统**创建者上线**时，插件把其创建的各系统未领取的收入发放给本人。

### 3.3 铁路系统 logo

支持Oraxen，使用铁路系统配置指令上传图片后，还需要手动重载资源包生效。

## 4. 车票系统指令

| 指令                                          | 权限                        | 说明                                                 |
|---------------------------------------------|---------------------------|----------------------------------------------------|
| ticket / ticketbg                           | bcts.ticket.open          | 打开车票购买界面 / 车票背景设置界面                                |
| ticketbg upload \<图片链接> \<背景名> \[字体颜色]      | bcts.ticket.uploadbg      | 上传车票/交通卡背景图，字体颜色格式 #RRGGBB（不填默认黑色）                 |
| ticketbg adminupload \<图片链接> \<背景名> \[字体颜色] | bcts.ticket.adminuploadbg | 以管理员身份上传共享背景图（无个数限制）                               |
| ticketbg delete \<图片id>                     | bcts.ticket.deletebg      | 根据 id 删除背景图                                        |
| ticketadmin reload                          | bcts.ticket.reload        | 重载所有配置文件                                           |
| ticketadmin migrate-olddb                   | bcts.ticket.migrate       | 从旧库 data.db 迁移交通卡与车票背景到新库 bcts.db                  |
| ticketadmin card give \<player> \[cardUUID] | bcts.ticket.getcard       | 给予玩家一张未开卡或已存在的交通卡                                  |
| ticketadmin card delete \<cardUUID>         | bcts.ticket.delcard       | 删除指定 UUID 的交通卡                                     |
| ticketadmin menuitem \<add/get> \<自定义物品名>   | bcts.ticket.menuitem      | 将手中物品保存到 menuitems.yml，或获取自定义物品，用于编辑菜单界面           |
| ticketadmin statistics \<type> \<days>      | bcts.ticket.statistics    | 查询 n 天内某类型的统计信息                                    |
| ticketdebug nbt \<key> \[value]             | bcts.ticket.nbt           | 查看/设置乘车凭证的 nbt，已定义的 nbt 见第 7 节                     |
| ticketdebug traininfo                       | bcts.ticket.debug         | 调试：输出当前所坐列车的信息                                     |
| ticketdebug switchtrace \<on/off>           | bcts.ticket.debug         | 调试：开关道岔选向追踪，开启后列车每经过 bcswitcher 打印选向到控制台           |
| ticketdebug slowdowntrace \<on/off>         | bcts.ticket.debug         | 调试：开关 slowdown 减速追踪，开启后列车经过 slowdown 打印预测与减速决策到控制台 |
| ticketdebug exportmmd                       | bcts.ticket.debug         | 调试：把 geojson 路由图与各线路子图导出为 Mermaid(.mmd) 到 mermaid/ |
| ticketconfig editRoute \<lineId>            | bcts.ticket.editroute     | 游戏内新建 / 修改线路配置（railway_routes.yml）                 |
| ticketconfig delRoute \<lineId>             | bcts.ticket.editroute     | 删除一条线路配置（railway_routes.yml）                       |
| ticketconfig editSystem \<systemId>         | bcts.ticket.editsystem    | 游戏内新建 / 修改铁路系统配置（railway_system.yml）               |
| ticketconfig delSystem \<systemId>          | bcts.ticket.editsystem    | 删除一个铁路系统配置（railway_system.yml），并连带删除其下所有线路         |
| 建立 bcspawn 控制牌                              | bcts.buildsign.bcspawn    |                                                    |
| 建立 platform 控制牌                             | bcts.buildsign.platform   |                                                    |
| 建立 bcswitcher 控制牌                           | bcts.buildsign.bcswitcher |                                                    |
| 建立 slowdown 控制牌                             | bcts.buildsign.slowdown   |                                                    |

## 5. 自定义菜单界面（menu_*.yml）

使用 [InvUI](https://github.com/NichtStudioCode/InvUI) 开发，可以自定义界面功能按钮的位置、材质、lore 等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，使用方法详见指令部分。

> 所有配置文件中的展示文字（界面名、物品名、lore、聊天提示、bossbar、进出站提示等）统一支持 **MiniMessage 标签** 与 **legacy `&` 颜色代码**，两种写法可在同一字符串内混用。

> 聊天栏 / bossbar 等提示文字统一放在 `messages.yml`（每条消息为一个根节点），与 `config.yml` 分开管理。

| 配置项       | 说明                                                     |
|-----------|--------------------------------------------------------|
| title     | 界面名，支持 MiniMessage 与 `&` 代码                            |
| structure | 界面结构，可以使用任意字符，字符间用空格分隔，界面尺寸为 n*9 n∈[1,6]               |
| mapping   | 界面结构的物品映射，格式：`字符 物品名`，物品名可使用 bukkit 物品枚举名或 item-自定义物品名 |

## 6. 可使用/预定义的界面结构映射物品名

| 界面           | 物品名                       | 功能                            |
|--------------|---------------------------|-------------------------------|
| 主界面          | content                   | 显示车票的格子                       |
| 主界面          | item-uses                 | 使用次数调整按钮                      |
| 主界面          | item-search               | 搜索车票按钮                        |
| 主界面          | item-warn                 | 注意事项按钮                        |
| 主界面          | item-ticketbgInfo         | 设置车票背景按钮                      |
| 主界面/设置车票背景界面 | item-prevpage             | 上一页按钮                         |
| 主界面/设置车票背景界面 | item-nextpage             | 下一页按钮                         |
| 主界面/交通卡界面    | item-start                | 起始站选择按钮（左键选铁路系统，Shift+左键搜索车站） |
| 主界面/交通卡界面    | item-end                  | 终到站选择按钮（左键选铁路系统，Shift+左键搜索车站） |
| 主界面/交通卡界面    | item-speed                | 速度调整按钮                        |
| 铁路系统选择界面     | content                   | 显示铁路系统的格子                     |
| 铁路系统选择界面     | item-system               | 铁路系统图标                        |
| 铁路系统选择界面     | item-prevpage             | 上一页按钮                         |
| 铁路系统选择界面     | item-nextpage             | 下一页按钮                         |
| 车站选择界面       | content                   | 显示车站的格子                       |
| 车站选择界面       | item-scrollup             | 滚动条向上滚动                       |
| 车站选择界面       | item-scrolldown           | 滚动条向下滚动                       |
| 设置车票背景界面     | item-selfbg               | 自己上传的背景                       |
| 设置车票背景界面     | item-sharedbg             | 对所有玩家公开使用的背景                  |
| 设置车票背景界面     | item-usedbg               | 当前使用的背景                       |
| 设置车票背景界面     | item-defaultbg            | 恢复默认背景图按钮                     |
| 设置车票背景界面     | item-sort                 | 排序按钮                          |
| 交通卡界面        | item-charge               | 充值按钮                          |
| 所有           | bukkit物品枚举名或item-其他自定义物品名 | 无功能，仅用于装饰                     |

> **车站按钮自定义图标**：在车站选择界面，铁路系统的成员可以把背包里的旗帜（不限颜色和图案）拖到某个车站按钮上，用该旗帜覆盖默认的车站图标（默认 MINECART），旗帜物品不会被消耗。
>
> - 仅当前车站所属铁路系统的成员可设置，图标归属该系统：同一车站在不同系统下可显示不同图标。
> - 通过**搜索按钮**进入车站选择界面时系统未知（一个车站可能属于多个系统），此时显示首个读取到的该车站图标。

## 7. 乘车凭证 nbt 定义

| 通用nbt                                  | 说明                 |
|----------------------------------------|--------------------|
| transitPassType                        | 凭证类型，ticket 或 card |
| plugin                                 | 固定为 bcts           |
| backgroundImagePath                    | 车票默认背景图片文件名        |
| bc-transit-pass（使用PDC，命名空间：bcts-guard） | 固定为 true，用于乘车凭证防复制 |

| 车票nbt                 | 说明                                           |
|-----------------------|----------------------------------------------|
| ticketName            | 车票名，应为 traincarts ticket.yml 中存在的车票名         |
| ticketCreationTime    | 车票购买时的时间戳                                    |
| ticketExpirationTime  | 车票过期时间（ms）                                   |
| ticketNumberOfUses    | 车票已经使用的次数                                    |
| ticketMaxNumberOfUses | 车票最大使用次数，应该小于 config.yml 中的 uses.max，负数为无限使用 |
| ticketOwner           | 车票购买者的 uuid                                  |
| ticketOwnerName       | 车票购买者的名字                                     |
| ticketOriginPrice     | 此车票单次票的价格                                    |
| startStation          | 起始站中文名                                       |
| endStation            | 终到站中文名                                       |
| startLineId           | 起始段所属线路 id                                   |
| ticketMaxSpeed        | 使用此车票上车后，列车的最大速度                             |
| ticketDistance        | 车票路线的距离                                      |

| 交通卡nbt       | 说明      |
|--------------|---------|
| cardUniqueID | 交通卡唯一标识 |
| cardInitFlag | 是否已经开卡  |

## 8. 控制牌新增/修改

### 8.1 announce

在 TrainCarts 原版基础上新增支持换行符 `\n`，一块牌可发送多行消息。

### 8.2 bcspawn

在 spawn 控制牌的基础上修改，用于发车：

- 第一行：\[+train\]（也可指定方向等，用法同其他控制牌第一行）
- 第二行：bcspawn \<格式同 traincarts 的 spawn>
- 第三行：\<线路 id> \<当前车站名>（第一个空格前为线路 id，其后为车站名）
- 第四行：\<格式同 traincarts 的 spawn>

发车后用线路 id 标记列车所属线路（供 bcswitcher 回退选向、platform 报站使用），并把当前车站名写入列车属性（用于车票校验与交通卡任意站台上车），同时把发车信息保存到数据库。

### 8.3 platform

站台控制牌（取代旧的 showroute），统一负责进出站提示、空车销毁与 bossbar 显示，放在进站报站和 station 控制牌之间。控制牌格式如下：

- 第一行：\[+train\]（也可以指定方向等，和其他控制牌第一行使用方法相同）
- 第二行：platform
- 第三行：\<本站名>（本站名要和配置文件中的车站名相同）
- 第四行：\[空格分隔的**不使用的**可选功能\]

可选功能：BB(BossBar)、DY(DestroY 空车销毁)、AN(Arrival-Notice 进站提示)、DN(Departure-Notice 出站提示)。

bossbar 分两种：

- **普通车**（不持票上车，每站停）：显示 “…上一站 → 当前站 → 下一站…” 滚动站名带，站序取自列车当前所属线路，在 `railway_routes.yml` 中的 `bossbar-stations`，首尾站名相同识别为环线（进度一直 100%）。列车每经过一个 platform
  控制牌推进一格；换乘到别的线路时按新线路重建。滚动样式（已过/未过站颜色与显示个数）见
  `config.yml` 的 `bossbar` 节点：`not-passed-color` 留空则使用该线路 `line-color`，颜色支持 `#RRGGBB` 或 `&` 代码。到站标题取自 `railway_routes.yml` 各线路的 `bossbar-arrival-notice`（占位符 `{curr_station}`），该线路留空则到站时显示滚动站名带。
- **直达车**（持票/刷卡上车，正线跨越中间站直达终点）：显示“起点 → 终点”+ 进度条，文案见 `config.yml` 的 `message.express-normal` / `message.express-end`。直达车不触发中间站的 platform 控制牌，进度由导航序列推进驱动（每经过一个 bcswitcher 道岔，进度 =
  已过道岔数 / 道岔总数）。

### 8.4 bcswitcher

道岔控制牌，声明该道岔的进入方向与各出向所属线路，并在运行时控制列车走向。控制牌格式如下：

- 第一行：\[+train:\<进入方向>\]（**进入方向必填**）
- 第二行：bcswitcher
- 第三行：\<出向>@\<线路id>\[;\<线路id>...\]
- 第四行：\<出向>@\<线路id>\[;\<线路id>...\]

**进入方向（必填）**：写在 `[+train:...]` 里，如 `[+train:lf]`。沿用 TrainCarts Direction 写法（`f`/`b`/`l`/`r` 或 `e`/`s`/`w`/`n`），可写多个。**不能为空、不能为 `*`**。它声明本牌只对从这些方向驶入的列车生效——遍历系统据此把道岔当作有向图节点，正确区分入边和出边。

**出向（第三、四行）**：格式 `出向@线路id`（`@` 两侧都不能为空）：

- **出向**：同样是 TrainCarts Direction 写法（`e`/`s`/`w`/`n` 或 `f`/`b`/`l`/`r`）。
- **线路 id**：`railway_routes.yml` 中定义的线路 id。
- **共用轨道**：一个出向被多条线路共用时，`@` 后用分号分隔多个线路 id，如 `r@pr-cw;pr-s1`。在路由图里它等于多条边（各属一条线），物理上是同一出向。

选向逻辑：

1. 列车有导航序列时（持票/刷卡的直达车），按导航当前应走的 lineId 匹配出向。
2. 无导航序列时（普通车），按线路属性 / tag 集合里第一个被命中的出向选向。
3. 都无匹配时保持默认方向（不切换）。

### 8.5 spawn

重写 build 方法，生成模型车不再检查 attachment editor 权限。

### 8.6 slowdown

减速控制牌，放在进站前、列车需要开始减速的位置，使列车到达前方 platform 时恰好减到设定速度。控制牌格式如下：

- 第一行：\[+train\]（也可指定方向等，用法同其他控制牌第一行）
- 第二行：slowdown
- 第三行：\[到达 platform 时的速度，支持 traincarts 的 launch 速度语法（如 `0.5`、`20km/h`），默认 0.2 block/tick\]

功能：列车经过本控制牌时，向前**预测**其将行驶的路径，直到找到 platform 控制牌，取 slowdown 到 platform 的距离，用一段 launch 动作让列车在这段距离内平滑减速到设定速度，恰好在
platform 处达到该速度。**只改速度不改最大速度**。

- 预测途中若先遇到**另一个 slowdown** 控制牌，则停止流程（减速由后者负责）。
- 超过 `config.yml` 的 `slowdown-max-detect-distance`（默认 500 格）仍未找到 platform 则不减速，防止 slowdown 放得过远导致的性能问题。
- **普通车**站站停车，固定减速到下一个 platform。
- **直达车**跨站直达，需额外判断终点：取列车**终点站台节点坐标**与预测到的 platform 铁轨坐标比对（用坐标而非站名——同站各站台 platform 站名相同，站名不可靠），相同才减速，防止在中途经过的 platform 处被误减速；取不到终点信息（如重启后内存丢失）则不减速。

**缓存机制**：每次预测开销较大，结果按车种缓存到数据库 `slowdown_cache` 表，启动与重载时载入内存。字段：slowdown 铁轨坐标 `world,x,y,z`、车种 `train_type`（express/common）、到达车站名 `station`、到达的 platform 铁轨坐标 `platform_x/y/z`、减速距离 `distance`。

## 9. 地理信息(GEO) / 实时数据模块

### 9.1 地理信息模块简介

沿真实轨道遍历铁路，把车站、道岔和区间导出为 geojson 格式，既用于车票寻路（第 2 节），也方便在地图应用上加载展示。

### 9.2 实时数据模块简介

将列车实时位置等信息通过 websocket 传输到前端显示（待开发）。

### 9.3 GEO 模块指令

以下所有指令需要 `bcts.railgeo` 权限：

| 指令                            | 功能                                |
|-------------------------------|-----------------------------------|
| railgeo walkAll               | 遍历所有已登记线路起点，按线路分文件保存为 geojson     |
| railgeo stopWalk              | 停止当前正在进行的铁轨遍历任务                   |
| railgeo setStartPos \<lineId> | 登记某线路的遍历起点，以玩家所在铁轨为起点坐标、面朝方向为起点方向 |
| railgeo delStartPos \<lineId> | 删除某线路已登记的遍历起点                     |

> 有 `bcts.railgeo.bypasscooldown` 权限者发起 `walkAll` 可**绕过全局冷却**。

### 9.4 线路配置（railway_routes.yml）

`railway_routes.yml` 每个顶层键是一个线路 id，与 bcswitcher / bcspawn 控制牌中使用的线路 id 对应。

线路配置项：

| 配置项                    | 说明                                                  |
|------------------------|-----------------------------------------------------|
| line-name              | 线路显示名称                                              |
| line-color             | 线路标志色（`#RRGGBB`）                                    |
| bossbar-stations       | 车站列表（普通车 bossbar 滚动用），首尾站名相同识别为环线；站名带 `:RV` 后缀表示折返站 |
| bossbar-arrival-notice | 到站时 bossbar 标题                                      |
| bossbar-color          | bossbar 颜色（Bukkit BossBar.Color 枚举名，如 RED）          |
| notice-arrival         | 进站提示列表，支持 `sound:...` 与 `announce:...`              |
| notice-departure       | 出站提示列表，同上                                           |

进出站提示可用占位符：`{curr_station}` 当前站、`{next_station}` 下一站、`{line_name}` 线路名、`{line_color}`线路标志色。

### 9.5 遍历流程与 geojson 结构

**遍历流程**（`railgeo walkAll`）：

1. 从数据库读出各条线路登记的起点（由 `railgeo setStartPos` 设置）。
2. 对每条线路做**有向图遍历**：以 bcswitcher / platform 为节点、其间铁路为有向边，从起点 BFS 展开。一节带该 lineId tag 的临时矿车按段行走（基于 TrainCarts 路径预测，正确触发沿途原版 switcher 的 addtag/remtag）：
    - 经过 **platform** 记为车站节点。按站名查 `railway_routes.yml`：普通站沿进入方向续行，折返站（`:RV`）反向驶出。
    - 经过 **bcswitcher** 记为道岔节点：枚举牌上 进入方向匹配本次到达方向、且归属当前线路 的所有出向，对每个出向各走一段（同一条线在一个道岔有多个出边时逐个走到，如正线 + 到发线）。
    - 去重 key = `(线路, 节点, 入向, 出向)`，既防环线 / 重复死循环，又保证共用轨道在每条线各自的文件里都完整。
    - 终止条件：所有可达状态展开完毕、或达到节点上限。
3. 遍历后按线把实际到达车站与配置 `bossbar-stations` 比对。**任一不一致即视为遍历失败**：立即中止、不写任何文件，并把详细原因（缺失：轨道未铺 / 道岔未声明该线；多余：站名写错 / 控制牌归属有误）反馈给发起者。起点坐标处无轨道、或达到段数上限时同样中止不写文件。

**运行约束与反馈**：

- **单运行 + 全局冷却**：同一时刻只允许一个遍历任务；完成后进入全局冷却（`config_geo.yml` 的 `traversal.cooldown-seconds`）。再次发起时若有任务在跑或仍在冷却，提示并拒绝。可用 `railgeo stopWalk` 提前停止；持 `bcts.railgeo.bypasscooldown` 权限者绕过冷却且不刷新冷却。
- **进度反馈**：遍历期间每隔 `traversal.progress-interval-seconds` 秒（默认 5，`<=0` 关闭）向发起者反馈“当前已遍历 N 个节点”；同时控制台也会输出进度。
- **暂停凭证**：遍历期间暂停车票 / 交通卡上车（普通车不受影响）。
- **与配置编辑互斥**：遍历期间禁用 `ticketconfig`（线路 / 铁路系统配置）指令；反之，发起遍历前若有玩家正在进行配置向导则拒绝遍历。避免配置改到一半时遍历，导致结果不一致。
- **遍历上限**：`traversal.max-edges-per-walk`（单段最多记录铁轨格数）、`traversal.max-total-nodes`（整次最多展开段数）可在 `config_geo.yml` 配置。
- **日志格式**：遍历日志（`<插件目录>/logs/railgeo_*.log`）。

**文件分组**：每条线路一个 `<lineId>.geojson`（含其经过的全部区间；共用轨道在各相关线路文件中均完整保存一份）。节点按物理坐标生成的 id（`n.world.x.y.z`）跨文件去重共享，累积经过它的所有线路 id。

**geojson 结构**：

- **Point**（节点）属性：`id`、`type`（`station` / `switch`）、`world`、`name`（仅 station）、`lineIds`（经过的线路 id 数组）、`prev` / `next`（前后相邻节点 id）。
- **LineString**（区间）属性：`id`、`from`、`to`、`lineId`、`world`、`color`、`length`、`layer`（webUI显示层级）`departDir`（从from节点选择什么方向可以到达这条线）。

### 9.6 标准车站格式

铁轨遍历的车站必须遵循 BC 国铁标准车站格式，否则可能出错，参考：https://www.yuque.com/sasanarx/bilicraft/svg7gi7htsm2prdc#q1uFT ，同时也是本车票系统建议的控制牌摆放方式。
