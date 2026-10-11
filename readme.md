# BiliCraft 车票系统

基于 TrainCarts 的车票系统。

- **不持票上车**：普通车，站站停，bossbar 显示滚动站名。
- **持票 / 刷交通卡上车**：直达车，按车票上的路线跨过中间站，直达终点。

本项目同时生成两个插件：

| 插件                    | 用途                                          |
|-----------------------|---------------------------------------------|
| BiliCraftTicketSystem | 车票系统本体                                      |
| BCTSGuardPlugin       | 防止玩家用制图台复制车票 / 交通卡。**群组服中没有铁路系统的服务器只需安装这个** |

两个插件**不能同时安装**在同一个服务器上（本体已包含防复制功能）。

## 1. 配置文件

| 文件                   | 内容                                |
|----------------------|-----------------------------------|
| `config.yml`         | 票价、速度、使用次数折扣、交通卡、搜索结果、bossbar、引导等 |
| `config_map.yml`     | 线路图构建（railgeo）、网页对接               |
| `railway_system.yml` | 铁路系统（建议在游戏内用指令编辑）                 |
| `railway_routes.yml` | 线路（建议在游戏内用指令编辑）                   |
| `messages.yml`       | 提示文字                              |
| `menu_*.yml`         | 各界面布局                             |
| `menuitems.yml`      | 界面中使用的自定义物品                       |

所有文字字符串都支持 **MiniMessage 标签**和 **`&` 颜色代码**，两种写法可以混用。

另外需要在 TrainCarts 中新建一张车票，名字与 `config.yml` 的 `express-ticket-name`（默认 `express`）一致，用于识别直达车。

## 2. 指令与权限

### 2.1 玩家指令

| 指令                                      | 权限                   | 说明                                  |
|-----------------------------------------|----------------------|-------------------------------------|
| `/ticket`                               | bcts.ticket.open     | 打开购票界面                              |
| `/ticketbg`                             | bcts.ticket.open     | 打开车票背景设置界面                          |
| `/ticketbg upload <图片链接> <背景名> [字体颜色]`  | bcts.ticket.uploadbg | 上传车票 / 交通卡背景，字体颜色格式 `#RRGGBB`，不填为黑色 |
| `/ticket weblogin <bind/unbind/status>` | bcts.ticket.weblogin | 绑定 / 解绑网页登录资格，或查看绑定状态               |

### 2.2 线路配置指令

| 指令                                | 权限                     | 说明                  |
|-----------------------------------|------------------------|---------------------|
| `/ticketconfig editSystem <系统id>` | bcts.ticket.editsystem | 新建 / 修改铁路系统         |
| `/ticketconfig delSystem <系统id>`  | bcts.ticket.editsystem | 删除铁路系统（会连带删除其下所有线路） |
| `/ticketconfig editRoute <线路id>`  | bcts.ticket.editroute  | 新建 / 修改线路           |
| `/ticketconfig delRoute <线路id>`   | bcts.ticket.editroute  | 删除线路                |


### 2.3 线路图构建指令

以下指令均需要 `bcts.railgeo` 权限：

| 指令                                      | 说明                           |
|-----------------------------------------|------------------------------|
| `/railgeo setStartPos <线路id>`           | 以脚下铁轨为起点、面朝方向为起点方向，登记线路的遍历起点 |
| `/railgeo delStartPos <线路id>`           | 删除线路的遍历起点                    |
| `/railgeo walk <线路id...>`               | 只重建指定的一条或多条线路（空格分隔）          |
| `/railgeo walkAll [--ignore <线路id...>]` | 重建全部线路；`--ignore` 后的线路跳过不建   |
| `/railgeo stopWalk`                     | 停止正在进行的构建（**不写入任何结果**）       |

`setStartPos` / `delStartPos` 只有该线路所属铁路系统的成员可以执行。使用说明见第 5 节。

### 2.4 管理员指令

| 指令                                                  | 权限                        | 说明                                    |
|-----------------------------------------------------|---------------------------|---------------------------------------|
| `/ticketadmin reload`                               | bcts.ticket.reload        | 重载所有配置文件                              |
| `/ticketadmin weblink <status/reconnect>`           | bcts.ticket.reload        | 查看网页后端连接状态 / 重连                       |
| `/ticketadmin weblink sync <geo/systems/lines/all>` | bcts.ticket.reload        | 手动向网页后端推送数据                           |
| `/ticketbg adminupload <图片链接> <背景名> [字体颜色]`         | bcts.ticket.adminuploadbg | 上传所有玩家都可使用的共享背景（不限个数）                 |
| `/ticketbg delete <图片id>`                           | bcts.ticket.deletebg      | 按 id 删除任意背景图                          |
| `/ticketadmin card give <玩家> [交通卡UUID]`             | bcts.ticket.getcard       | 给予玩家一张新交通卡，或一张已存在的交通卡                 |
| `/ticketadmin card delete <交通卡UUID>`                | bcts.ticket.delcard       | 删除交通卡                                 |
| `/ticketadmin menuitem <add/get> <物品名>`             | bcts.ticket.menuitem      | 把手中物品存为自定义物品 / 取出自定义物品（用于编辑界面，见第 6 节） |
| `/ticketadmin statistics <类型> <天数>`                 | bcts.ticket.statistics    | 查询近 n 天的统计数据                          |
| `/ticketadmin migrate-olddb`                        | bcts.ticket.migrate       | 从旧版本数据库 `data.db` 迁移交通卡与车票背景          |

### 2.5 调试指令

| 指令                                    | 权限                | 说明                               |
|---------------------------------------|-------------------|----------------------------------|
| `/ticketdebug traininfo`              | bcts.ticket.debug | 查看当前所坐列车的信息（含直达车路线与进度）           |
| `/ticketdebug switchtrace <on/off>`   | bcts.ticket.debug | 列车每经过一个 bcswitcher，在控制台打印选向结果    |
| `/ticketdebug slowdowntrace <on/off>` | bcts.ticket.debug | 列车每经过一个 slowdown，在控制台打印减速判断      |
| `/ticketdebug exportmmd`              | bcts.ticket.debug | 把线路图导出为 Mermaid 图到 `mermaid/` 目录 |
| `/ticketdebug nbt <键> [值]`            | bcts.ticket.nbt   | 查看 / 设置手中车票或交通卡的数据（见第 8 节）       |

### 2.6 其它权限

| 权限                        | 说明                     |
|---------------------------|------------------------|
| bcts.buildsign.bcspawn    | 放置 bcspawn 控制牌         |
| bcts.buildsign.platform   | 放置 platform 控制牌        |
| bcts.buildsign.bcswitcher | 放置 bcswitcher 控制牌      |
| bcts.buildsign.slowdown   | 放置 slowdown 控制牌        |
| bcts.buildsign.isexpress  | 放置 isexpress 控制牌       |
| bcts.buildsign.switchline | 放置 switchline 控制牌      |
| bcts.bypass               | 发起线路图构建时绕过冷却（且不刷新冷却时间） |

## 3. 控制牌

### 3.1 bcspawn（发车）

在 TrainCarts `spawn` 控制牌基础上修改。

```
[+train]
bcspawn <同 TrainCarts spawn>
<线路id> <当前车站名>
<同 TrainCarts spawn>
```

第三行第一个空格前是线路 id，之后是车站名。持票玩家必须在车票记录的起点站、使用对应线路的发车装置上车。

### 3.2 platform（站台）

负责进出站提示、空车销毁与 bossbar 显示，一般相邻 station 控制牌放置。

```
[+train]
platform
<本站名>
[不使用的功能]
```

- 本站名必须与线路配置 `bossbar-stations` 中的车站名一致。
- 第四行填**不需要**的功能（空格分隔）：`BB`（bossbar）、`DY`（空车销毁）、`AN`（进站提示）、`DN`（出站提示）。留空表示全部启用。

### 3.3 bcswitcher（道岔）

声明道岔的进入方向和各出向所属线路，并在列车经过时自动切换道岔。

```
[+train:<进入方向>]
bcswitcher
<出向>@<线路id>[;<线路id>...]
<出向>@<线路id>[;<线路id>...]
```

- **进入方向（必填）**：本牌只对从这些方向驶入的列车生效。写法同 TrainCarts：`f`/`b`/`l`/`r` 或 `e`/`s`/`w`/`n`，可以写多个，如 `[+train:lf]`。**不能为空，不能写 `*`**。
- **出向**：写法同 TrainCarts switcher：`e`/`s`/`w`/`n`、`f`/`b`/`l`/`r`。
- **共用轨道**：多条线路走同一个出向时，用分号分隔，如 `r@pr-cw;pr-s1`。也可以分成两块牌分别写 `r@pr-cw`、`r@pr-s1`，效果相同。
- **联络线**：出向的线路 id 写 `contact`。
- **TCCoasters 轨道**：用数字节点名代替方向，如 `[+train:1]`、`2@pr-cw`。多个进入节点用逗号分隔，如 `[+train:1,3]`。方向字符与数字节点名不能混写。
- 同一格铁轨可以放多块 bcswitcher。

### 3.4 slowdown（进站减速）

放在进站前需要开始减速的位置，列车到达前方 platform 时恰好减到设定速度，支持直达车和普通车。

```
[+train]
slowdown
[到达 platform 时的速度]
```

- 速度写法同 TrainCarts launch（如 `0.5`、`20km/h`），不填默认 0.2 block/tick。只改变速度，不改变最大速度。
- 超过 `config.yml` 的 `slowdown-max-detect-distance`（默认 500 格）仍没找到 platform 则不减速，请把 slowdown 放在合理距离内。
- 直达车只在终点站减速，中途经过的站台不减速。

### 3.5 isexpress（车种判别）

```
[+train]
isexpress
```

直达车经过时拉下与本牌相连的拉杆，普通车经过时复位。

### 3.6 switchline（转线）

```
[train]
switchline
<下一条线路id>:<下一站车站名>
```

收到红石信号时，把本牌所在铁轨上的列车改为属于指定线路。

### 3.7 其它修改

- **announce**：支持用 `\n` 换行，一块牌发送多行消息。
- **spawn**：生成模型车不再要求 attachment editor 权限。
- **showroute**：已弃用，由 platform 取代。

## 4. 线路与铁路系统配置

### 4.1 铁路系统（railway_system.yml）

| 配置项                                | 说明                                |
|------------------------------------|-----------------------------------|
| `name`                             | 铁路系统名称（必填）                        |
| `price-per-km`                     | 每公里票价（选填，不填使用 `config.yml` 的全局票价） |
| `members`                          | 成员 UUID 列表                        |
| `creator` / `income` / `withdrawn` | 创建者、累计收入、已发放收入，**插件自动维护，请勿修改**    |

- 允许设置的票价范围由 `config.yml` 的 `price-per-km-range` 限制。
- 铁路系统 logo：在编辑向导中上传图片。使用 Oraxen 时需要手动重载资源包才能生效。
- `contact`（联络线）是内置的特殊系统，请勿删除。

### 4.2 线路（railway_routes.yml）

每个顶层键是一个线路 id，与控制牌中的线路 id 对应。

| 配置项                      | 说明                                                   |
|--------------------------|------------------------------------------------------|
| `railway-system`         | 所属铁路系统 id（必填）                                        |
| `line-name`              | 线路名称                                                 |
| `line-color`             | 线路标志色，`#RRGGBB`                                      |
| `bossbar-stations`       | 车站列表（必填），见下                                          |
| `bossbar-arrival-notice` | 到站时的 bossbar 标题；留空则显示滚动站名                            |
| `bossbar-color`          | bossbar 颜色，如 `RED`、`BLUE`                            |
| `notice-arrival`         | 进站提示列表，每项为 `sound:<音效名>,<音调>,<音量>` 或 `announce:<文字>` |
| `notice-departure`       | 出站提示列表，格式同上                                          |

**车站列表 `bossbar-stations` 的写法：**

- 按列车行驶顺序列出车站名。首尾站名相同表示环线。
- 站名后加 `:RV` 表示折返站（列车进站后反向驶出），如 `StationA:RV`。
- 最后一项写 `<线路id>` 或 `<线路id>:<进入站名>` 表示**转线**：普通车到达本线终点站后，转到该线路继续运行，进入站名是转线后的下一站。

**提示文字中可用的占位符：** `{curr_station}` 当前站、`{next_station}` 下一站、`{line_name}` 线路名、`{line_color}` 线路标志色、`{railway_system}` 铁路系统名。

`contact` 是联络线专用的线路 id，请勿删除。

## 5. 构建线路图（railgeo）

购票寻路依赖线路图。线路图由插件沿轨道遍历生成，保存在 `plugins/BiliCraftTicketSystem/geojson/` 下，每条线路一个文件。支持普通铁轨和 TCCoasters 轨道。

### 5.1 注意事项

- 每条线路都要先用 `setStartPos` 登记起点才能构建。
- 联络线（`contact`）不需要登记起点。
- 构建期间：车票和交通卡暂停使用（普通车不受影响）；`/ticketconfig` 指令不可用；有玩家正在使用配置向导时不能开始构建。
- 同一时间只能进行一个构建任务。`walk` 和 `walkAll` 完成后各有冷却时间（见 5.3）。
- 完整日志在 `plugins/BiliCraftTicketSystem/logs/railgeo_*.log`。

### 5.2 常见问题

| 提示                                  | 原因与处理                                                          |
|-------------------------------------|----------------------------------------------------------------|
| 配置车站 "xxx" 未在遍历中到达                  | 轨道没铺到，或途中某个 bcswitcher 没有为这条线声明出向。提示下方会列出遍历走过的路径，最后一行就是走不下去的位置 |
| 以下线路没有已登记的遍历起点                      | 用 `/railgeo setStartPos <线路id>` 登记起点                           |
| 起点坐标处没有铁轨                           | 起点处的轨道被拆了，重新登记起点                                               |
| 达到单元段数上限                            | 线路中可能有缺少控制牌的环路，或线路确实很大。检查轨道，或调大 `max-edges-per-unit`           |
| 同时存在 bcswitcher 与 platform 控制牌      | 两者挂在了同一格铁轨上，请按标准车站格式分开放置                                       |
| 道岔声明了通往 xxx 的出向，但其落点不在线路 xxx 的遍历结果中 | 该出向的线路 id 可能写错了，或对方线路的轨道没铺到那里                                  |
| 该道岔没有匹配此到达方向的 xxx 出向                | 换乘 / 转线处道岔的进入方向没有包含来车方向，检查牌头 `[+train:...]`                    |

### 5.3 配置（config_map.yml 的 traversal 节点）

| 配置项                         | 默认值   | 说明                                                 |
|-----------------------------|-------|----------------------------------------------------|
| `max-edges-per-walk`        | 50000 | 相邻两个节点之间最多记录的铁轨格数                                  |
| `max-edges-per-unit`        | 20000 | 单条线路最多展开的区间数，超出则该线路构建失败                            |
| `interval-ticks`            | 3     | 每隔多少 tick 推进一批                                     |
| `edges-per-interval`        | 1     | 每批推进多少个区间。调大可加快构建，但会增加服务器负担                        |
| `progress-interval-seconds` | 5     | 进度提示间隔（秒），`<=0` 关闭                                 |
| `cooldown-seconds`          | 3600  | `walkAll` 完成后的冷却（秒）                                |
| `walk-cooldown-seconds`     | 300   | `walk` 完成后的冷却（秒）                                   |
| `coaster-sample-step`       | 1.0   | tcc轨道曲线的采样间距（格），越小弧线越平滑。`<=0` 时弧线画成直线              |
| `trace-max-chat-lines`      | -1    | 构建失败时最多在聊天栏输出多少行路径，`-1` 不限制，`0` 不输出（日志文件中始终保存完整信息） |

## 6. 自定义界面（menu_*.yml）

界面使用 [InvUI](https://github.com/NichtStudioCode/InvUI) 开发，可以自定义按钮位置、材质和 lore，除原版物品外还支持 Oraxen 物品和头颅。

| 配置项         | 说明                                                      |
|-------------|---------------------------------------------------------|
| `title`     | 界面名                                                     |
| `structure` | 界面结构，任意字符，字符间用空格分隔，尺寸为 n×9（n 为 1~6）                     |
| `mapping`   | 字符与物品的映射，格式 `字符 物品名`。物品名可用 Bukkit 物品枚举名，或 `item-自定义物品名` |

自定义物品用 `/ticketadmin menuitem add <物品名>` 把手中物品保存到 `menuitems.yml`。

**预定义的功能按钮：**

| 界面                    | 物品名                                 | 功能                          |
|-----------------------|-------------------------------------|-----------------------------|
| 主界面                   | `content`                           | 显示车票的格子                     |
| 主界面                   | `item-uses`                         | 调整使用次数                      |
| 主界面                   | `item-search`                       | 搜索车票                        |
| 主界面                   | `item-warn`                         | 注意事项                        |
| 主界面                   | `item-ticketbgInfo`                 | 设置车票背景                      |
| 主界面 / 交通卡界面           | `item-start`                        | 选择起始站（左键选铁路系统，Shift+左键搜索车站） |
| 主界面 / 交通卡界面           | `item-end`                          | 选择终到站（同上）                   |
| 主界面 / 交通卡界面           | `item-speed`                        | 调整速度                        |
| 交通卡界面                 | `item-charge`                       | 充值                          |
| 主界面 / 背景界面 / 铁路系统选择界面 | `item-prevpage` / `item-nextpage`   | 上一页 / 下一页                   |
| 铁路系统选择界面              | `content`                           | 显示铁路系统的格子                   |
| 铁路系统选择界面              | `item-system`                       | 铁路系统图标                      |
| 车站选择界面                | `content`                           | 显示车站的格子                     |
| 车站选择界面                | `item-scrollup` / `item-scrolldown` | 向上 / 向下滚动                   |
| 车站选择界面 / 铁路系统选择界面     | `item-back`                         | 返回上一级                       |
| 背景界面                  | `item-selfbg`                       | 自己上传的背景                     |
| 背景界面                  | `item-sharedbg`                     | 公开共享的背景                     |
| 背景界面                  | `item-usedbg`                       | 当前使用的背景                     |
| 背景界面                  | `item-defaultbg`                    | 恢复默认背景                      |
| 背景界面                  | `item-sort`                         | 排序                          |
| 背景界面                  | `item-back`                         | 返回主界面                       |
| 所有界面                  | 其它物品名                               | 仅作装饰                        |

**车站图标**：铁路系统成员可以把背包里的旗帜拖到车站选择界面中自己系统的车站按钮上，用这面旗帜作为该车站的图标。

## 7. 票价与收入

- **票价**：路线经过的每段轨道，按该段所属铁路系统的每公里票价乘以距离累加。玩家是某个铁路系统的成员时，该系统的路段免费。
- **折扣**：按使用次数（`config.yml` 的 `uses.discount`）与权限组（`perm-discount`）叠加计算。
- **交通卡**：每次刷卡另收起步价（`card.base-fare`）。起步价计入行程起点所在的铁路系统；刷卡人是该系统成员时免收。
- **收入**：玩家实际支付的金额按各铁路系统的路段费用比例分给各系统。铁路系统创建者上线时，插件自动把未领取的收入发给本人。
- **上车**：车票只能在车票记录的起点站上车；交通卡不指定起点站时可以在任意站台上车，会从当前站台计算到终点的最短路线。
- **寻找站台**：手持车票右键，action bar 会显示前往上车站台的方向与距离，站台处有站名提示。

## 8. 车票 / 交通卡数据（nbt）

可用 `/ticketdebug nbt <键> [值]` 查看或修改。

| 通用                    | 说明                     |
|-----------------------|------------------------|
| `transitPassType`     | 凭证类型，`ticket` 或 `card` |
| `plugin`              | 固定为 `bcts`             |
| `backgroundImagePath` | 背景图片文件名                |

| 车票                                | 说明                                          |
|-----------------------------------|---------------------------------------------|
| `ticketName`                      | 车票名，须为 TrainCarts `ticket.yml` 中存在的车票       |
| `ticketCreationTime`              | 购买时间戳                                       |
| `ticketExpirationTime`            | 过期时间（ms）                                    |
| `ticketNumberOfUses`              | 已使用次数                                       |
| `ticketMaxNumberOfUses`           | 最大使用次数，应小于 `config.yml` 的 `uses.max`，负数为无限次 |
| `ticketOwner` / `ticketOwnerName` | 购买者 UUID / 名字                               |
| `ticketOriginPrice`               | 单次票价                                        |
| `startStation` / `endStation`     | 起始站 / 终到站                                   |
| `startLineId`                     | 起始段所属线路 id                                  |
| `ticketMaxSpeed`                  | 列车最大速度                                      |
| `ticketDistance`                  | 路线距离                                        |

| 交通卡            | 说明      |
|----------------|---------|
| `cardUniqueID` | 交通卡唯一标识 |
| `cardInitFlag` | 是否已开卡   |

## 9. 网页线路图对接

插件可以连接配套的网页线路图（项目 [railway-map-v2](https://github.com/BigBrother-666/railway-map-v2)），在网页上显示线路、列车实时位置，并支持网页在线购票。在 `config_map.yml` 的 `web-link` 节点配置：

| 配置项                              | 说明                       |
|----------------------------------|--------------------------|
| `enabled`                        | 是否启用网页对接                 |
| `backend-url`                    | 网页后端地址                   |
| `shared-token`                   | 与后端一致的密钥，**请务必修改为安全的密钥** |
| `server-id`                      | 服务器标识                    |
| `max-reconnect-seconds`          | 断线重连的最长间隔（秒）             |
| `telemetry.enabled`              | 是否推送列车实时位置               |
| `telemetry.interval-ticks`       | 推送间隔（tick）               |
| `telemetry.include-empty-trains` | 是否推送没有乘客的列车              |
| `purchase.enabled`               | 是否允许网页购票                 |
| `purchase.max-uses`              | 网页购票单次允许的最大使用次数          |
| `purchase.require-online`        | 是否要求玩家在线才能网页购票（车票直接送到背包） |

- 线路图构建完成后自动推送到网页；`/ticketadmin reload` 后推送线路与铁路系统信息；也可用 `/ticketadmin weblink sync` 手动推送。
- 玩家用 `/ticket weblogin bind` 获得网页登录资格。
