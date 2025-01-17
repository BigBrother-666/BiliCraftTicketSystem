# BiliCraft车票系统

## 1. 简介

在TrainCarts车票系统的基础上开发的适用于BiliCraft的车票系统。玩家可以持票/不持票上车，持票上车会根据车票的tag直达终点站，不持票上车则每站都会停车（站站乐）。

## 2. 站台判断
在使用车票前会判断乘客是否在正确的站台，根据车票的`站台tag`（格式为`本站tag-方向`）和`列车tag`判断乘客的车票是否可用，两者的`本站tag`必须相同，`列车tag`的`方向`为`车票tag`的`方向`的前缀。

## 3. 路径查找

`routes.txt`使用Mermaid抽象车站之间的关系为有向图，每行的格式为`前一站的车站名-铁路名-方向-跨越本站需要的tag-->|距离|后一站的车站名-铁路名-方向-跨越本站需要的tag`。
其他参考配置文件内注释。

## 4. 自定义菜单界面（menu_*.yml）

可以自定义菜单功能按钮或其他物品的位置、材质、lore等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，详见下文指令部分。

## 5. 指令

| 指令/功能                                    | 权限                     | 说明                                                                           |
|------------------------------------------|------------------------|------------------------------------------------------------------------------|
| ticket                                   | bcts.ticket.open       | 打开车票购买界面                                                                     |
| ticket reload                            | bcts.ticket.reload     | 重载所有配置文件                                                                     |
| ticket item add/get <自定义物品名>             | bcts.ticket.menuitem   | 将手中的物品保存到配置文件（menuitems.yml）/获取自定义物品，以便编辑菜单界面使用。使用时，在material后面填写item-自定义物品名 |
| ticket nbt \<key> \[value]               | bcts.ticket.nbt        | 查看/设置车票的nbt，已经定义的nbt信息如下表所示                                                  |
| ticket statistics ticket/bcspawn \<days> | bcts.ticket.statistics | 查看车票/发车的统计信息                                                                 |
| ticket co add \<站台tag>                   | bcts.ticket.co         | 从co数据库导入发车信息(需要指针对准按钮/压力板)                                                   |
| 建立bcspawn控制牌                             | bcts.buildsign.bcspawn |                                                                              |

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

## 6. 控制牌新增/更改
### 5.1 announce
新增支持换行符`\n`。
### 5.2 bcspawn
在spawn控制牌的基础上修改，第四行改为列车生成后赋予的tag。
### 5.3 showroute
在boss栏显示路线信息
- 第一行：[+train]
- 第二行：showroute
- 第三行：\<路线id> \<本站名>
- 第四行：\<color> \<color>

