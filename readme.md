# BiliCraft车票系统

## 1. 简介

在TrainCarts车票系统的基础上开发的适用于BiliCraft国铁模式的车票系统。玩家可以持票/不持票上车，持票上车会根据车票的tag直达终点站，不持票上车则每站都会停车（站站乐）。

同时生成的BctsGuard插件可以防止玩家使用制图台复制车票/交通卡，两个插件不可以同时安装在一个服务器，没有铁路系统的服务器只需安装BctsGuard插件。

## 2. 路径查找

`routes.mmd`使用Mermaid抽象车站之间的关系为有向图，每行的格式为`车站名-铁路名-方向-本站tag==>|距离|下一站的车站名-铁路名-方向-本站tag`，最终构成一个完整的有向图。
查询车票时，程序会根据mermaid构建的有向图查询出两车站间所有可能的路线，玩家在正确的站台使用车票上车后，列车会被赋予一系列tag，这些tag即为途径的车站的`车站tag`。
所以每个车站要根据`本站tag`放置switcher控制牌，来引导列车从正线通过。
本系统建议的控制牌摆放方式：https://www.yuque.com/sasanarx/bilicraft/svg7gi7htsm2prdc#q1uFT

## 3. 站台判断

在使用车票前会判断乘客是否在正确的站台。本系统为每个车站的每个站台都定义了一个独特的“站台tag”，格式为：`本站tag-方向`，`本站tag`和`方向`和`routes.mmd`中的相同（只是位置反了）。
发车时，列车应被赋予这个tag，来和玩家手中车票的“站台tag”比较，进而判断玩家所在站台是否正确。

## 4. 指令

| 指令/功能                                             | 权限                        | 说明                                                                                               |
|---------------------------------------------------|---------------------------|--------------------------------------------------------------------------------------------------|
| ticket / ticket bg                                | bcts.ticket.open          | 打开车票购买界面 / 车票背景界面                                                                                |
| ticket addroute \<routeid>                        | bcts.ticket.addroute      | 添加或修改showroute控制牌的路径                                                                             |
| ticket delroute \<routeid>                        | bcts.ticket.delroute      | 删除showroute控制牌的路径                                                                                |
| ticket uploadbg \<图片链接> \<自定义背景图名> \[车票字体颜色]      | bcts.ticket.uploadbg      | 上传背景图片，颜色格式 #RRGGBB                                                                              |
| ticket deletebg \<图片id>                           | bcts.ticket.deletebg      | 指令删除任意背景图 / gui删除任意共享的背景图                                                                        |
| ticket adminuploadbg \<图片链接> \<自定义背景图名> \[车票字体颜色] | bcts.ticket.adminuploadbg | 以管理员身份上传背景图片（没有个数限制）                                                                             |
| ticket reload                                     | bcts.ticket.reload        | 重载所有配置文件                                                                                         |
| ticket getcard                                    | bcts.ticket.getcard       | 获取一张交通卡，第一次拿在主手会自动初始化                                                                            |
| ticket item \<add/get> <自定义物品名>                   | bcts.ticket.menuitem      | 将手中的物品保存到配置文件（menuitems.yml）或获取自定义物品，以便编辑菜单界面使用。使用时，在mapping列表填写item-自定义物品名，所有功能按钮的物品名已经预定义，详见下表 |
| ticket nbt \<key> \[value]                        | bcts.ticket.nbt           | 查看/设置车票的nbt，已经定义的nbt信息如下表所示                                                                      |
| ticket statistics \<ticket/bcspawn> \<days>       | bcts.ticket.statistics    | 查看车票/发车的统计信息                                                                                     |
| ticket co add \<站台tag>                            | bcts.ticket.co            | 从co数据库导入发车信息(需要指针对准按钮/压力板)                                                                       |
| 建立bcspawn控制牌                                      | bcts.buildsign.bcspawn    |                                                                                                  |
| 建立showroute控制牌                                    | bcts.buildsign.showroute  |                                                                                                  |

## 5. 自定义菜单界面（menu_*.yml）

使用[InvUI](https://github.com/NichtStudioCode/InvUI)开发，可以自定义界面功能按钮的位置、材质、lore等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，使用方法详见下文指令部分。

| 配置项                        | 说明                                                                 |
|----------------------------|--------------------------------------------------------------------|
| title                      | 界面名，支持MiniMessage                                                  |
| structure                  | 界面结构，可以使用任意字符，字符间用空格分隔，界面尺寸为n*9 n∈[1,6]                            |
| mapping                    | 界面结构的物品映射，格式：`字符 物品名`，物品名可使用bukkit物品枚举名或item-自定义物品名                |
| content（menu_location.yml） | 车站物品列表，键可以随意定义（一般是车站名），name为车站名，material可使用bukkit物品枚举名或item-自定义物品名 |

## 6. 可使用/预定义的界面结构映射物品名

| 界面            | 物品名                       | 功能           |
|---------------|---------------------------|--------------|
| 主界面           | content                   | 显示车票的格子      |
| 主界面           | item-start                | 起始站选择按钮      |
| 主界面           | item-end                  | 终到站选择按钮      |
| 主界面           | item-speed                | 速度调整按钮       |
| 主界面           | item-uses                 | 使用次数调整按钮     |
| 主界面           | item-search               | 搜索车票按钮       |
| 主界面           | item-warn                 | 注意事项按钮       |
| 主界面/设置车票背景界面  | item-prevpage             | 上一页按钮        |
| 主界面/设置车票背景界面  | item-nextpage             | 下一页按钮        |
| 主界面           | item-filter               | 筛选按钮         |
| 主界面           | item-ticketbgInfo         | 设置车票背景按钮     |
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
| 所有            | bukkit物品枚举名或item-其他自定义物品名 | 无功能，仅用于装饰    |

此外，自定义物品`selected`被预定义为筛选界面车站选择后显示的物品。

## 7. 车票nbt定义

| nbt                   | 说明                                        |
|-----------------------|-------------------------------------------|
| ticketName            | 车票名，应为traincarts ticket.yml中存在的车票名        |
| ticketDisplayName     | 显示在地图上的车票名，默认值为ticketName                 |
| ticketCreationTime    | 车票购买时的时间戳                                 |
| ticketNumberOfUses    | 车票已经使用的次数                                 |
| ticketMaxNumberOfUses | 车票最大使用次数，应该小于config.yml中的uses.max，负数为无限使用 |
| ticketOwner           | 车票购买者的uuid                                |
| ticketOwnerName       | 车票购买者的名字                                  |
| ticketMaxSpeed        | 使用此车票上车后，列车的最大速度                          |
| ticketOriginPrice     | 此车票单次票的价格                                 |
| ticketItemName        | 车票物品名                                     |
| ticketTags            | 车票的tags，使用英文逗号分隔的tag                      |
| startPlatformTag      | 可使用站台的tag                                 |
| startStation          | 起始站中文名                                    |
| endStation            | 终到站中文名                                    |
| distance              | 两站间距                                      |
| backgroundImagePath   | 车票背景图片文件名                                 |

## 8. 控制牌新增/修改

### 8.1 announce

新增支持换行符`\n`。

### 8.2 bcspawn

在spawn控制牌的基础上修改，第四行改为列车生成后赋予的tag（相当于spawn+addtag），一般第四行指定用于站台判断的tag，并且会将发车信息保存到数据库。

### 8.3 showroute

在boss栏显示路线信息，并会根据经过的车站数量自动更新bossbar进度（环线进度一直是100%，指定的route的第一站和最后一站相同会被识别为环线），该控制牌需要放在进站报站和station控制牌之间。

需要注意的是，双线铁路的两个方向都需要配置，任意两条路线的id不能相同。
控制牌格式如下：

- 第一行：\[+train\]  (也可以指定方向等，和其他控制牌第一行使用方法相同)
- 第二行：showroute
- 第三行：<路线id> <本站名>   (本站名要和配置文件中的车站名相同)
- 第四行：<args*5>

第四行的五个参数分别为：BOSS条颜色 已经过的车站格式 未经过的车站格式 已经过的车站最大显示个数 未经过的车站最大显示个数
控制牌第四行和配置文件格式相同，优先级大于配置文件，一般不需要填写（除非想要临时改变样式），第四行留空即可。
原理：列车经过showroute控制牌时显示到站信息（curr-station-title指定的内容），离开station控制牌时显示线路图。

控制牌的参数优先级大于配置文件。

### 8.4 station

更改了列车进入和离开station时的逻辑，用来配合showroute控制牌。不使用showroute时，表现同原版TC。

### 8.5 spawn

重写build方法，生成模型车不再检查attachment editor权限。

### 8.6 property-remtag

列车进入remtag property控制牌时，根据剩余tag数量更新bossbar进度。

## 9. 地理信息(GEO) / 实时数据模块

### 9.1 地理信息模块简介

将符合国铁模式的铁路（见9.5 标准车站格式）和车站坐标导出为geojson格式，方便在地图应用上加载

### 9.2 实时数据模块简介

将列车实时位置等信息通过websocket传输到前端显示（待开发）。

### 9.3 指令

以下所有指令需要`bcts.railgeo`权限

| 指令                                         | 功能                                                                                                                                           |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| railgeo start \[--less-log\]               | 开始铁路遍历，使用此命令前，需要用`railgeo setStartPos`添加遍历起始点坐标，`--less-log`代表在聊天栏输出更少的log，完整log可在插件配置文件夹下的logs文件夹查看                                         |
| railgeo setStartPos <站台tag>                | 设置玩家当前位置为某站台的遍历起点，开始坐标为玩家脚下的铁轨坐标，开始遍历方向为玩家的面朝方向，遍历起点站台为程序自动根据mermaid图计算（入度为0的点即为开始点）， 自动补全的`<站台tag>`参数即为需要添加的开始点                             |
| railgeo stop                               | 停止当前未完成的遍历任务                                                                                                                                 |
| railgeo update \[--less-log\]              | 在新建站点后，可以使用此命令更新geojson文件，程序会根据mermaid新增的节点自动对geojson文件进行新增/更新                                                                               |
| railgeo setLine <站台tag> <line_in/line_out> | 对于特殊的车站（比如尽头式车站），程序自动遍历时会出错（进站出站线路多样，不易自动化），则使用此指令手动标记该站台的进站/出站线路，如果没有此类特殊站点，可不使用此命令。输入指令后，左键点击的铁轨会设置为开始点，点击铁轨时的玩家朝向会设置为遍历方向，右键点击的铁轨会设置为终点铁轨 |
| railgeo delManualNode <站台tag>              | 删除`railgeo setLine`手动设置的进站/出站线路                                                                                                              |

### 9.4 线路种类介绍

一个geojson文件包含一个站台坐标点和多条线路，线路解释如下：

| 线路种类id           | 线路种类名称 | 范围                                                                                              |
|------------------|--------|-------------------------------------------------------------------------------------------------|
| line_in          | 进站线路   | 从跨站的switcher控制牌到bcspawn控制牌的一段线路，只有带正线的车站才有进站线路                                                  |
| line_out         | 出站线路   | 从bcspawn控制牌到正线和侧线交点的一段线路，只有带正线的车站才有出站线路                                                         |
| main_line_first  | 第一段正线  | 从跨站的switcher控制牌到正线和侧线交点的一段线路，只有带正线的车站才有第一段正线                                                    |
| main_line_second | 第二段正线  | 从正线和侧线交点到下一个车站的跨站的switcher控制牌（下一站有正线）或下一个车站的bcspawn控制牌/station控制牌（下一站没有正线）的一段线路，可能有多个（两站之间有联络线） |

### 9.5 标准车站格式

铁轨遍历的车站必须遵循bc国铁标准车站格式，否则可能会出现错误，参考：https://www.yuque.com/sasanarx/bilicraft/svg7gi7htsm2prdc#q1uFT ，同时也是本车票系统建议的控制牌摆放方式。
