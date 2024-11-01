# BiliCraft车票系统

## 1. 简介

在TrainCarts车票系统的基础上开发的适用于BiliCraft的车票系统。玩家可以持票/不持票上车，持票上车会根据车票的tag直达终点站，不持票上车则每站都会停车（站站乐）。

## 2. 路径查找

`routes.txt`使用Mermaid抽象车站之间的关系为有向图，每行的格式为`前一站的车站名-铁路名-方向-跨越本站需要的tag-->|距离|后一站的车站名-铁路名-方向-跨越本站需要的tag`。
其他参考配置文件内注释。

## 3. 自定义菜单界面（menu_*.yml）

可以自定义菜单功能按钮或其他物品的位置、材质、lore等信息。除了原版材质，还能使用材质包物品（oraxen）、头颅等，详见下文指令部分。

## 4. 指令

| 指令                             | 权限                   | 说明                                                                           |
|--------------------------------|----------------------|------------------------------------------------------------------------------|
| ticket                         | bcts.ticket.open     | 打开车票购买界面                                                                     |
| ticket reload                  | bcts.ticket.reload   | 重载所有配置文件                                                                     |
| ticket item <add/get> <自定义物品名> | bcts.ticket.menuitem | 将手中的物品保存到配置文件（menuitems.yml）/获取自定义物品，以便编辑菜单界面使用。使用时，在material后面填写item-自定义物品名 |

## 5. 控制牌更改
### 5.1 Announce
新增支持换行符`\n`。