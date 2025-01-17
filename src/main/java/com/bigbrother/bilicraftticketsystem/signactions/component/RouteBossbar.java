package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
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
        bossBar.progress((float) (nextStationIdx - 1) / routeList.size());
    }

    private String getNextTitle() {
        StringBuilder title = new StringBuilder();
        int displayNum = args.notPassedNum + args.passedNum;
        int size = routeList.size();

        if (isRing() && size - 1 <= displayNum) {
            List<String> temp = new ArrayList<>(routeList);
            temp.remove(temp.get(size - 1));
            for (int i = 0; i < size; i++) {
                if (i < nextStationIdx) {
                    if (i == 0) {
                        title.append(args.passedColor).append(temp.get(i));
                    } else {
                        title.append("%s → ".formatted(args.passedColor)).append(temp.get(i));
                    }
                } else {
                    title.append("%s → ".formatted(args.notPassedColor)).append(temp.get(i));
                }
            }
        } else if (!isRing() && size <= displayNum) {
            for (int i = 0; i < size; i++) {
                if (i < nextStationIdx) {
                    if (i == 0) {
                        title.append(args.passedColor).append(routeList.get(i));
                    } else {
                        title.append("%s → ".formatted(args.passedColor)).append(routeList.get(i));
                    }
                } else {
                    title.append("%s → ".formatted(args.notPassedColor)).append(routeList.get(i));
                }
            }
        } else {
            if (isRing()) {
                // 环线
                title.append("%s... → ".formatted(args.passedColor));
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
                    buildTitle(0, end, title, temp);
                } else if (end > size) {
                    // end越界
                    while (end > size) {
                        title.append("%s → ".formatted(args.passedColor)).append(temp.get(end - size));
                        --end;
                    }
                    buildTitle(start, size, title, temp);
                } else {
                    buildTitle(start, end, title, temp);
                }
                title.append("%s → ...".formatted(args.notPassedColor));
            } else {
                int start, end;
                start = Math.max(nextStationIdx - args.passedNum, 0);
                end = Math.min(nextStationIdx + args.notPassedNum, size);

                if (end - start < displayNum) {
                    if (start == 0) {
                        end = start + displayNum;
                        buildTitle(start, end, title, routeList);
                        title.append("%s → ...".formatted(args.notPassedColor));
                    } else {
                        title.append("%s... → ".formatted(args.passedColor));
                        buildTitle(start, end, title, routeList);
                        start = end - displayNum;
                    }
                }

                buildTitle(start, end, title, routeList);
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

    @Data
    public static class Args {
        public Args(String[] args) {
            if (args.length == 5) {
                this.bossbarColor = BossBar.Color.valueOf(args[0].trim().toUpperCase());
                this.passedColor = "&" + args[1];
                this.notPassedColor = "&" + args[2];
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
