package com.bigbrother.bilicraftticketsystem.addon.signactions.component;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import lombok.Data;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RouteBossbar {
    @Getter
    private final BossBar bossBar;
    @Getter
    private String routeId;
    private ArrayList<String> routeList;
    private int nextStationIdx;
    private Args args;

    // 直达车变量
    private String expressEnd;
    int totalTagNum = 0;

    public RouteBossbar(String routeId, Args args) {
        String route = RailwayRoutesConfig.railwayRoutes.get("%s.route".formatted(routeId.trim()), String.class, null);
        if (route == null) {
            this.routeList = null;
            this.bossBar = null;
            this.routeId = null;
            return;
        }
        this.routeId = routeId;
        this.nextStationIdx = 0;

        this.routeList = new ArrayList<>(Arrays.asList(route.split("->")));
        this.bossBar = BossBar.bossBar(Component.text(""),
                0,
                args.getBossbarColor(),
                BossBar.Overlay.PROGRESS);
    }

    // 直达车bossbar构造方法
    public RouteBossbar(String startStation, String endStation, int totalTagNum) {
        this.totalTagNum = totalTagNum + 1;
        if (startStation == null || endStation == null) {
            this.routeList = null;
            this.bossBar = null;
            this.routeId = null;
            return;
        }
        this.expressEnd = endStation;
        this.bossBar = BossBar.bossBar(CommonUtils.mmStr2Component(MainConfig.message.get("express-normal", "").formatted(startStation, endStation)), 0.0F, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
    }

    public void updateStation() {
        if (nextStationIdx < 0 || nextStationIdx >= routeList.size()) {
            return;
        }

        this.bossBar.color(this.args.bossbarColor);
        if (isRing()) {
            bossBar.progress((float) 1.0);
        } else {
            bossBar.progress(Math.min((float) (nextStationIdx + 1) / routeList.size(), 1.0f));
        }

        String title = RailwayRoutesConfig.railwayRoutes.get("%s.curr-station-title".formatted(routeId.trim()), String.class, null);
        if (title == null) {
            bossBar.name(CommonUtils.legacyStr2Component(getNextTitle()));
        } else {
            bossBar.name(CommonUtils.legacyStr2Component(title.replace("{station}", routeList.get(nextStationIdx))));
        }
    }

    public void update(String[] args, String currStation, String routeId) {
        if (!routeId.equals(this.routeId)) {
            String route = RailwayRoutesConfig.railwayRoutes.get("%s.route".formatted(routeId.trim()), String.class, null);
            if (route == null) {
                // 不执行更新
                return;
            }
            this.routeId = routeId;
            this.routeList = new ArrayList<>(Arrays.asList(route.split("->")));
        }
        nextStationIdx = routeList.indexOf(currStation);
        this.args = new Args(args);
    }

    public void update() {
        nextStationIdx += 1;
        bossBar.name(CommonUtils.legacyStr2Component(getNextTitle()));
        if (isRing()) {
            bossBar.progress((float) 1.0);
        } else {
            bossBar.progress(Math.min((float) (nextStationIdx) / routeList.size(), 1.0f));
        }
    }

    public void updateExpress(int leftTagCount) {
        bossBar.progress(Math.max(0, 1 - (float) leftTagCount / totalTagNum));
        if (leftTagCount == 0 && expressEnd != null) {
            bossBar.name(CommonUtils.mmStr2Component(MainConfig.message.get("express-end", "").formatted(expressEnd)));
        }
    }

    private String getNextTitle() {
        StringBuilder title = new StringBuilder();
        int displayNum = args.notPassedNum + args.passedNum;
        int size = routeList.size();

        if (!isRing() && size <= displayNum) {
            buildTitle(0, size, title, routeList);
        } else {
            if (isRing()) {
                // 环线
                title.append("%s...".formatted(args.passedColor));
                List<String> temp = new ArrayList<>(routeList);
                temp.remove(size - 1);
                size = temp.size();

                int start = nextStationIdx - args.passedNum;
                int end = nextStationIdx + args.notPassedNum;

                if (start < 0) {
                    // start越界
                    while (start < 0) {
                        title.append("%s → ".formatted(args.passedColor)).append(temp.get(size + start));
                        ++start;
                    }
                    title.append("%s →".formatted(args.passedColor));
                    buildTitle(0, end, title, temp);
                } else if (end > size) {
                    // end越界
                    title.append("%s → ".formatted(args.passedColor));
                    buildTitle(start, size, title, temp);
                    int tempCnt = 0;
                    while (tempCnt < end - size) {
                        title.append("%s → ".formatted(args.notPassedColor)).append(temp.get(tempCnt));
                        ++tempCnt;
                    }
                } else {
                    title.append("%s → ".formatted(args.passedColor));
                    buildTitle(start, end, title, temp);
                }
                title.append("%s → ...".formatted(args.notPassedColor));
            } else {
                int start, end;
                start = Math.max(nextStationIdx - args.passedNum, 0);
                end = Math.min(nextStationIdx + args.notPassedNum, size);

                if (end - start <= displayNum) {
                    if (start == 0) {
                        end = start + displayNum;
                        buildTitle(start, end, title, routeList);
                        title.append("%s → ...".formatted(args.notPassedColor));
                    } else {
                        start = end - displayNum;
                        title.append("%s... → ".formatted(args.passedColor));
                        buildTitle(start, end, title, routeList);
                        if (end != size) {
                            title.append("%s → ...".formatted(args.notPassedColor));
                        }
                    }
                } else {
                    title.append("%s... → ".formatted(args.passedColor));
                    buildTitle(start, end, title, routeList);
                    title.append("%s → ...".formatted(args.notPassedColor));
                }
            }
        }
        return title.toString();
    }

    private void buildTitle(int start, int end, StringBuilder title, List<String> l) {
        for (int i = start; i < end; i++) {
            if (i < nextStationIdx) {
                if (i == start) {
                    title.append(args.passedColor).append(l.get(i));
                } else {
                    title.append("%s → ".formatted(args.passedColor)).append(l.get(i));
                }
            } else {
                title.append("%s → ".formatted(args.notPassedColor)).append(l.get(i));
            }
        }
    }

    private boolean isRing() {
        return routeList.get(0).equals(routeList.get(routeList.size() - 1));
    }

    private static String convert(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            result.append('&').append(c);
        }
        return result.toString();
    }

    @Data
    public static class Args {
        public Args(String[] args) {
            if (args.length == 5) {
                this.bossbarColor = BossBar.Color.valueOf(args[0].trim().toUpperCase());
                this.passedColor = convert(args[1].trim());
                this.notPassedColor = convert(args[2].trim());
                this.passedNum = Integer.parseInt(args[3].trim());
                this.notPassedNum = Integer.parseInt(args[4].trim());
            }
        }

        private BossBar.Color bossbarColor;
        private String passedColor;
        private String notPassedColor;
        private int passedNum;
        private int notPassedNum;
    }
}
