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
- 寻路得到的路线会推导出一条 各道岔应选线路 的导航序列（见下 直达车导航机制），写入列车属性，列车据此在每个 bcswitcher 自动选向。

### 直达车导航机制

直达车根据**导航序列**——途经各 bcswitcher 应选的线路 id 有序列表，存于列车属性（记录TC的ITrainProperty）：

- 购票/刷卡上车时，由寻路结果（`GeoRoutePath.switcherLineIds()`）生成导航序列写入列车。
- 列车每经过一个 bcswitcher，该道岔按导航序列**当前项**选向，随后导航指针推进一格。
- bossbar 进度 = 已过道岔数 / 道岔总数；导航走完即视为到达终点区段（以后可能会升级为基于剩余距离的进度显示）。

调试用 `/ticket traininfo` 查看某列车的导航序列与进度，用 `/ticket switchtrace on` 把每次道岔选向打印到控制台。

## 3. 站台与上车判断

车票记录了起点站名与起点线路ID。发车控制牌（bcspawn）会把列车的起点车站名和线路ID写入列车属性，玩家使用车票上车时据此校验是否在正确的车站。交通卡支持任意站台上车，从玩家所在站台节点重新寻路到终点。

## 4. 车票系统指令

| 指令                                          | 权限                        | 说明                                       |
|---------------------------------------------|---------------------------|------------------------------------------|
| ticket / ticket bg                          | bcts.ticket.open          | 打开车票购买界面 / 车票背景设置界面                      |
| ticket uploadbg \<图片链接> \<背景名> \[字体颜色]      | bcts.ticket.uploadbg      | 上传车票/交通卡背景图，字体颜色格式 #RRGGBB（不填默认黑色）       |
| ticket adminuploadbg \<图片链接> \<背景名> \[字体颜色] | bcts.ticket.adminuploadbg | 以管理员身份上传共享背景图（无个数限制）                     |
| ticket deletebg \<图片id>                     | bcts.ticket.deletebg      | 根据 id 删除背景图                              |
| ticket reload                               | bcts.ticket.reload        | 重载所有配置文件                                 |
| ticket migrate-olddb                        | bcts.ticket.migrate       | 从旧库 data.db 迁移交通卡与车票背景到新库 bcts.db        |
| ticket card give \<player> \[cardUUID]      | bcts.ticket.getcard       | 给予玩家一张未开卡或已存在的交通卡                        |
| ticket card delete \<cardUUID>              | bcts.ticket.delcard       | 删除指定 UUID 的交通卡                           |
| ticket menuitem \<add/get> \<自定义物品名>        | bcts.ticket.menuitem      | 将手中物品保存到 menuitems.yml，或获取自定义物品，用于编辑菜单界面 |
| ticket nbt \<key> \[value]                  | bcts.ticket.nbt           | 查看/设置乘车凭证的 nbt，已定义的 nbt 见第 7 节           |
| ticket font                                 | bcts.ticket.font          | 查询系统注册的字体列表                              |
| ticket statistics \<type> \<days>           | bcts.ticket.statistics    | 查询 n 天内某类型的统计信息                          |
| ticket traininfo                            | bcts.ticket.traininfo     | 调试：输出当前所坐列车的信息                           |
| ticket switchtrace \<on/off>                | bcts.ticket.traininfo     | 调试：开关道岔选向追踪，开启后列车每经过 bcswitcher 打印选向到控制台 |
| 建立 bcspawn 控制牌                              | bcts.buildsign.bcspawn    |                                          |
| 建立 platform 控制牌                             | bcts.buildsign.platform   |                                          |

## 5. 自定义菜单界面（menu_*.yml）

使用 [InvUI](https://github.com/NichtStudioCode/InvUI) 开发，可以自定义界面功能按钮的位置、材质、lore 等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，使用方法详见指令部分。

> 所有配置文件中的展示文字（界面名、物品名、lore、聊天提示、bossbar、进出站提示等）统一支持 **MiniMessage 标签** 与 **legacy `&` 颜色代码**，两种写法可在同一字符串内混用。

| 配置项       | 说明                                                     |
|-----------|--------------------------------------------------------|
| title     | 界面名，支持 MiniMessage 与 `&` 代码                            |
| structure | 界面结构，可以使用任意字符，字符间用空格分隔，界面尺寸为 n*9 n∈[1,6]               |
| mapping   | 界面结构的物品映射，格式：`字符 物品名`，物品名可使用 bukkit 物品枚举名或 item-自定义物品名 |

## 6. 可使用/预定义的界面结构映射物品名

| 界面            | 物品名                       | 功能           |
|---------------|---------------------------|--------------|
| 主界面           | content                   | 显示车票的格子      |
| 主界面           | item-uses                 | 使用次数调整按钮     |
| 主界面           | item-search               | 搜索车票按钮       |
| 主界面           | item-warn                 | 注意事项按钮       |
| 主界面           | item-filter               | 筛选按钮         |
| 主界面           | item-ticketbgInfo         | 设置车票背景按钮     |
| 主界面/设置车票背景界面  | item-prevpage             | 上一页按钮        |
| 主界面/设置车票背景界面  | item-nextpage             | 下一页按钮        |
| 主界面/交通卡界面     | item-start                | 起始站选择按钮      |
| 主界面/交通卡界面     | item-end                  | 终到站选择按钮      |
| 主界面/交通卡界面     | item-speed                | 速度调整按钮       |
| 车站选择界面/车站筛选界面 | content                   | 显示车站的格子      |
| 车站选择界面/车站筛选界面 | item-scrollup             | 滚动条向上滚动      |
| 车站选择界面/车站筛选界面 | item-scrolldown           | 滚动条向下滚动      |
| 车站筛选界面        | item-refresh              | 清空所有筛选       |
| 车站筛选界面        | item-back                 | 确定并返回主菜单     |
| 设置车票背景界面      | item-selfbg               | 自己上传的背景      |
| 设置车票背景界面      | item-sharedbg             | 对所有玩家公开使用的背景 |
| 设置车票背景界面      | item-usedbg               | 当前使用的背景      |
| 设置车票背景界面      | item-defaultbg            | 恢复默认背景图按钮    |
| 设置车票背景界面      | item-sort                 | 排序按钮         |
| 交通卡界面         | item-charge               | 充值按钮         |
| 所有            | bukkit物品枚举名或item-其他自定义物品名 | 无功能，仅用于装饰    |

此外，自定义物品 `selected` 被预定义为筛选界面车站选择后显示的物品。

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

- **普通车**（不持票上车，每站停）：显示 “…上一站 → 当前站 → 下一站…” 滚动站名带，站序取自列车当前所属线路，在 `routes.yml` 中的 `bossbar-stations`，首尾站名相同识别为环线（进度一直 100%）。列车每经过一个 platform 控制牌推进一格；换乘到别的线路时按新线路重建。滚动样式（已过/未过站颜色与显示个数）见
  `config.yml` 的 `bossbar` 节点：`not-passed-color` 留空则使用该线路 `line-color`，颜色支持 `#RRGGBB` 或 `&` 代码。到站标题取自 `routes.yml` 各线路的 `bossbar-arrival-notice`（占位符 `{curr_station}`），该线路留空则到站时显示滚动站名带。
- **直达车**（持票/刷卡上车，正线跨越中间站直达终点）：显示「起点 → 终点」+ 进度条，文案见 `config.yml` 的 `message.express-normal` / `message.express-end`。直达车不触发中间站的 platform 控制牌，进度由导航序列推进驱动（每经过一个 bcswitcher 道岔，进度 = 已过道岔数 / 道岔总数）。

### 8.4 bcswitcher

道岔控制牌，声明该道岔可通向哪些线路，并在运行时控制列车走向。控制牌格式如下：

- 第一行：\[+train\]（也可以指定方向等）
- 第二行：bcswitcher
- 第三行：\<方向>@\<线路id>
- 第四行：\<方向>@\<线路id>

第三、四行各声明一个分支，格式为 `方向@线路id`（`@` 两侧都不能为空）：

- **方向**：沿用 TrainCarts 的 Direction 写法——绝对方向 `e`/`s`/`w`/`n`（东/南/西/北），或相对方向 `f`/`b`/`l`/`r`（前/后/左/右，相对牌子朝向）。
- **线路 id**：可以是 `routes.yml` 中定义的普通线路 id，或特殊 id `contact`（联络线）、`default`（到发线/正线）。

选向逻辑：

1. 列车有导航序列时（持票/刷卡的直达车），按导航当前应走的 lineId 精确匹配分支方向（见 8.5）。
2. 无导航序列时（普通车），按列车 tag 集合里第一个匹配的 lineId 选向。
3. 都无匹配时保持默认方向（不切换）。

### 8.6 spawn

重写 build 方法，生成模型车不再检查 attachment editor 权限。

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
| railgeo walk \<lineId>        | 单线调试遍历：从玩家当前位置和朝向起步，按指定线路 id 走一条线 |
| railgeo setStartPos \<lineId> | 登记某线路的遍历起点，以玩家所在铁轨为起点坐标、面朝方向为起点方向 |
| railgeo delStartPos \<lineId> | 删除某线路已登记的遍历起点                     |

### 9.4 线路配置（routes.yml）

`routes.yml` 每个顶层键是一个线路 id，与 bcswitcher / bcspawn 控制牌中使用的线路 id 对应。两个特殊 id：

- **contact**：联络线（区间之间的连接线，地图上以中性色显示）。
- **default**：车站正线（跨站行驶的轨道，没有正线的车站则从到发线跨越）。

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

进出站提示可用占位符：`{curr_station}` 当前站、`{next_station}` 下一站、`{line_name}` 线路名。`contact` 与 `default` 只需配置 `line-name`（及可选 `line-color`）。

### 9.5 遍历流程与 geojson 结构

**遍历流程**（`railgeo walkAll`）：

1. 从数据库读出各条线路登记的起点（由 `railgeo setStartPos` 设置）。
2. 对每条线路，一节带该 lineId tag 的临时矿车从起点沿轨道行走（基于 TrainCarts 的路径预测，所有道岔按 tag 决定走向）：
    - 经过 **platform** 记为车站节点，并按 `routes.yml` 的 `bossbar-stations` 顺序校验站名；
    - 经过 **bcswitcher** 记为道岔节点；若该道岔含 `default` 分支，额外走一段正线子遍历并入当前线路文件；若含 `contact` 分支，记录下来，待主线遍历完成后遍历。
    - 终止条件：配置车站全部按序到齐、环线闭合、或轨道结束。
3. 联络线（contact）在所有线路遍历完后统一遍历，单独成文件。

**文件分组**：每条线路一个 `<lineId>.geojson`（含其正线区间），联络线汇总到 `contact.geojson`。节点按物理坐标生成的 id（`n.world.x.y.z`）跨文件去重共享，累积经过它的所有线路 id。

**geojson 结构**：

- **Point**（节点）属性：`id`、`type`（`station` / `switch`）、`name`（仅 station）、`lineIds`（经过的线路 id 数组）、`prev` / `next`（前后相邻节点 id）。
    - **LineString**（区间）属性：`id`、`from`、`to`、`lineId`、`color`、`length`、`layer`（webUI显示层级）。

### 9.6 标准车站格式

铁轨遍历的车站必须遵循 BC 国铁标准车站格式，否则可能出错，参考：https://www.yuque.com/sasanarx/bilicraft/svg7gi7htsm2prdc#q1uFT ，同时也是本车票系统建议的控制牌摆放方式。
