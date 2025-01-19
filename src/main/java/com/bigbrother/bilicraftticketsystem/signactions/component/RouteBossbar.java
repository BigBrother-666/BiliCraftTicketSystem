package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionShowroute;
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

    public RouteBossbar(String routeId, Args args) {
        String route = MainConfig.railwayRoutes.get("%s.route".formatted(routeId.trim()), String.class, null);
        if (route == null) {
            routeList = null;
            bossBar = null;
            return;
        }
        this.routeId = routeId;

        this.routeList = new ArrayList<>(Arrays.asList(route.split("->")));
        this.bossBar = BossBar.bossBar(Component.text(""),
                0,
                args.getBossbarColor(),
                BossBar.Overlay.PROGRESS);
    }

    public RouteBossbar(String ticketDisplayName) {
        String[] split = ticketDisplayName.split(" → ");
        if (split.length != 2) {
            bossBar = null;
            return;
        }
        this.bossBar = BossBar.bossBar(Utils.str2Component("&6%s &a====== 直达 ======>> &6%s".formatted(split[0], split[1])), 1.0F, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
    }

    public void updateStation() {
        String title = MainConfig.railwayRoutes.get("%s.curr-station-title".formatted(routeId.trim()), String.class, null);
        if (title == null) {
            return;
        }
        bossBar.name(Utils.str2Component(title.replace("{station}", routeList.get(nextStationIdx))));
        if (isRing()) {
            bossBar.progress((float) 1.0);
        } else {
            bossBar.progress((float) (nextStationIdx) / routeList.size());
        }
    }

    public void update(String[] args, String currStation, String routeId) {
        if (!routeId.equals(this.routeId)) {
            String route = MainConfig.railwayRoutes.get("%s.route".formatted(routeId.trim()), String.class, null);
            if (route == null) {
                routeList = null;
                return;
            }
            this.routeId = routeId;
        }
        nextStationIdx = routeList.indexOf(currStation) + 1;
        this.args = new Args(args);
        bossBar.name(Utils.str2Component(getNextTitle()));
        if (isRing()) {
            bossBar.progress((float) 1.0);
        } else {
            bossBar.progress((float) (nextStationIdx) / routeList.size());
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
                this.passedColor = convert(args[1]);
                this.notPassedColor = convert(args[2]);
                this.passedNum = Integer.parseInt(args[3]);
                this.notPassedNum = Integer.parseInt(args[4]);
            }
        }

        private BossBar.Color bossbarColor;
        private String passedColor;
        private String notPassedColor;
        private int passedNum;
        private int notPassedNum;
    }
}
