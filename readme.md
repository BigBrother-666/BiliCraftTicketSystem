# BiliCraft车票系统

## 1. 简介
在TrainCarts车票系统的基础上开发的适用于BiliCraft国铁模式的车票系统。玩家可以持票/不持票上车，持票上车会根据车票的tag直达终点站，不持票上车则每站都会停车（站站乐）。


## 2. 站台判断
在使用车票前会判断乘客是否在正确的站台。要使用此特性，需要在每个车站为列车赋予一个特殊的tag，格式为：`本站tag-方向`，`本站tag`和`方向`和`routes.txt`中的相同。


## 3. 路径查找
`routes.txt`使用Mermaid抽象车站之间的关系为有向图，每行的格式为`车站名-铁路名-方向-本站tag-->|距离|下一站的车站名-铁路名-方向-本站tag`，最终构成一个完整的有向图。


## 4. 指令
| 指令/功能                                       | 权限                       | 说明                                                                                               |
|---------------------------------------------|--------------------------|--------------------------------------------------------------------------------------------------|
| ticket                                      | bcts.ticket.open         | 打开车票购买界面                                                                                         |
| ticket reload                               | bcts.ticket.reload       | 重载所有配置文件                                                                                         |
| ticket item \<add/get> <自定义物品名>             | bcts.ticket.menuitem     | 将手中的物品保存到配置文件（menuitems.yml）或获取自定义物品，以便编辑菜单界面使用。使用时，在mapping列表填写item-自定义物品名，所有功能按钮的物品名已经预定义，详见下表 |
| ticket nbt \<key> \[value]                  | bcts.ticket.nbt          | 查看/设置车票的nbt，已经定义的nbt信息如下表所示                                                                      |
| ticket statistics \<ticket/bcspawn> \<days> | bcts.ticket.statistics   | 查看车票/发车的统计信息                                                                                     |
| ticket co add \<站台tag>                      | bcts.ticket.co           | 从co数据库导入发车信息(需要指针对准按钮/压力板)                                                                       |
| 建立bcspawn控制牌                                | bcts.buildsign.bcspawn   |                                                                                                  |
| 建立showroute控制牌                              | bcts.buildsign.showroute |                                                                                                  |


## 5. 自定义菜单界面（menu_*.yml）
使用[InvUI](https://github.com/NichtStudioCode/InvUI)开发，可以自定义界面功能按钮的位置、材质、lore等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，使用方法详见下文指令部分。

| 配置项                        | 说明                                                                 |
|----------------------------|--------------------------------------------------------------------|
| title                      | 界面名，支持MiniMessage                                                  |
| structure                  | 界面结构，可以使用任意字符，字符间用空格分隔，界面尺寸为n*9 n∈[1,6]                            |
| mapping                    | 界面结构的物品映射，格式：`字符 物品名`，物品名可使用bukkit物品枚举名或item-自定义物品名                |
| content（menu_location.yml） | 车站物品列表，键可以随意定义（一般是车站名），name为车站名，material可使用bukkit物品枚举名或item-自定义物品名 |


## 6. 可使用/预定义的界面结构映射物品名
| 界面     | 物品名                       | 功能        |
|--------|---------------------------|-----------|
| 主界面    | content                   | 显示车票的格子   |
| 主界面    | item-start                | 起始站选择按钮   |
| 主界面    | item-end                  | 终到站选择按钮   |
| 主界面    | item-speed                | 速度调整按钮    |
| 主界面    | item-uses                 | 使用次数调整按钮  |
| 主界面    | item-search               | 搜索车票按钮    |
| 主界面    | item-warn                 | 注意事项按钮    |
| 车站选择界面 | content                   | 显示车站的格子   |
| 车站选择界面 | item-scrollup             | 滚动条向上滚动   |
| 车站选择界面 | item-scrolldown           | 滚动条向下滚动   |
| 所有     | bukkit物品枚举名或item-其他自定义物品名 | 无功能，仅用于装饰 |


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

## 8. 控制牌新增/更改

### 8.1 announce
新增支持换行符`\n`。


### 8.2 bcspawn
在spawn控制牌的基础上修改，第四行改为列车生成后赋予的tag（相当于spawn+addtag），一般第四行指定用于站台判断的tag，并且会将发车信息保存到数据库。


### 8.3 showroute
在boss栏显示路线信息，并会根据经过的车站数量自动更新bossbar进度（环线进度一直是100%，指定的route的第一站和最后一站相同会被识别为环线），该控制牌需要放在进站报站和station控制牌之间。

需要注意的是，双线铁路的两个方向都需要配置，任意两条路线的id不能相同。
控制牌格式如下：
- 第一行：[+train]  (也可以指定方向等，和其他控制牌第一行使用方法相同)
- 第二行：showroute
- 第三行：<路线id> <本站名>   (本站名要和配置文件中的车站名相同)
- 第四行：<args*5>

第四行的五个参数分别为：BOSS条颜色 已经过的车站格式 未经过的车站格式 已经过的车站最大显示个数 未经过的车站最大显示个数
控制牌第四行和配置文件格式相同，优先级大于配置文件，一般不需要填写（除非想要临时改变样式），第四行留空即可。
原理：列车经过showroute控制牌时显示到站信息（curr-station-title指定的内容），离开station控制牌时显示线路图。

控制牌的参数优先级大于配置文件


### 8.4 station
更改了列车进入和离开station时的逻辑，用来配合showroute控制牌。不使用showroute时，表现同原版TC。