package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.route.geograph.GeoGraphLoader;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteGraph;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.JourneyPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.util.List;

/**
 * 用真实服务器 geojson 复现卡顿。仅在传入 -Dbcts.geodir=... 时启用。
 * 运行：mvn -o test -pl bcts-plugin -am -Dtest=RealGraphReproTest \
 *        -Dbcts.geodir=/data/temp/bilicraft/servers/paper-1.21.11/plugins/BiliCraftTicketSystem/geojson \
 *        -Dsurefire.failIfNoSpecifiedTests=false
 */
public class RealGraphReproTest {

    private static final String START = "东海望";
    private static final String END = "南湖林地";

    @Test
    @EnabledIfSystemProperty(named = "bcts.geodir", matches = ".+")
    void reproduce() {
        File dir = new File(System.getProperty("bcts.geodir"));
        GeoRouteGraph g = new GeoGraphLoader(null).loadDir(dir);
        GeoRouteEngine.setGraph(g);
        System.out.println("[repro] nodes=" + g.nodeCount() + " links=" + g.linkCount());

        long t0 = System.nanoTime();
        List<GeoRoutePath> direct = GeoRouteEngine.findByStation(START, END, 10);
        long t1 = System.nanoTime();
        System.out.printf("[repro] findByStation(10) -> %d paths in %.1f ms%n",
                direct.size(), (t1 - t0) / 1e6);

        long t2 = System.nanoTime();
        List<GeoRoutePath> directAll = GeoRouteEngine.findByStation(START, END, 0);
        long t3 = System.nanoTime();
        System.out.printf("[repro] findByStation(0/unlimited) -> %d paths in %.1f ms%n",
                directAll.size(), (t3 - t2) / 1e6);

        long t4 = System.nanoTime();
        List<JourneyPlan> plans = GeoRouteEngine.findTransferJourneys(START, END, 3);
        long t5 = System.nanoTime();
        System.out.printf("[repro] findTransferJourneys(3) -> %d plans in %.1f ms%n",
                plans.size(), (t5 - t4) / 1e6);
    }
}
