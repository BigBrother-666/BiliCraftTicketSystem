package com.bigbrother.bilicraftticketsystem.route.geodata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.Point;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 删除线路时同步清理 geojson：让删除线路 / 铁路系统后无需重新 {@code walkAll}。
 * <p>
 * 只删 {@code <lineId>.geojson} 不够：别的线路文件里还有 {@code lineId} 为被删线路的区间
 * （通往它的越界边、转线落点补边），不清掉的话地图上仍会画出这些残段、寻路也会走进去。故分两步：
 * <ol>
 *   <li>删除被删线路自己的文件；</li>
 *   <li>其余文件中删掉 {@code lineId} 属被删线路的区间，并整理节点：从 {@code lineIds} 中去掉被删线路、
 *       删掉不再被任何区间引用的节点、按剩余区间重算 {@code prev} / {@code next}。</li>
 * </ol>
 * 不重算 layer：删掉区间只可能让某些线不再需要抬层，保持原 layer 显示上没有问题，下次遍历会自然校正。
 */
public class GeodataLineRemover {

    /**
     * 清理结果。
     *
     * @param deletedFiles   删除的文件数（被删线路自己的文件）
     * @param rewrittenFiles 因含被删线路的区间而被改写的其它文件数
     */
    public record Result(int deletedFiles, int rewrittenFiles) {
    }

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从 geojson 目录中清除指定线路的全部数据。
     *
     * @param geodataDir geojson 目录
     * @param lineIds    被删除的线路 id
     * @param systemIds  被删除的铁路系统 id（只删线路时传空集合），从车站节点的 {@code railwaySystemIds} 中去掉
     * @return 清理结果
     * @throws IOException 读写文件失败
     */
    public Result removeLines(File geodataDir, Collection<String> lineIds, Collection<String> systemIds)
            throws IOException {
        Set<String> removed = new LinkedHashSet<>(lineIds);
        Set<String> removedSystems = new LinkedHashSet<>(systemIds);
        File[] files = geodataDir.listFiles((d, name) -> name.toLowerCase().endsWith(".geojson"));
        if (files == null) {
            return new Result(0, 0);
        }
        int deleted = 0;
        int rewritten = 0;
        for (File file : files) {
            String name = file.getName();
            String fileLineId = name.substring(0, name.length() - ".geojson".length());
            if (removed.contains(fileLineId)) {
                if (file.delete()) {
                    deleted++;
                } else {
                    throw new IOException("删除文件失败：" + name);
                }
                continue;
            }
            FeatureCollection fc = mapper.readValue(file, FeatureCollection.class);
            if (prune(fc, removed, removedSystems)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, fc);
                rewritten++;
            }
        }
        return new Result(deleted, rewritten);
    }

    /**
     * 从一个 FeatureCollection 中删掉属于被删线路的区间，并整理节点。纯逻辑，便于单元测试。
     *
     * @param fc             待处理的 FeatureCollection（原地修改）
     * @param removed        被删除的线路 id
     * @param removedSystems 被删除的铁路系统 id
     * @return 有改动返回 true
     */
    public static boolean prune(FeatureCollection fc, Set<String> removed, Set<String> removedSystems) {
        if (fc.getFeatures() == null) {
            return false;
        }
        boolean changed = false;
        // 1. 删掉被删线路的区间
        Iterator<Feature> it = fc.getFeatures().iterator();
        while (it.hasNext()) {
            Feature f = it.next();
            if (f.getGeometry() instanceof LineString && removed.contains(str(f.getProperty("lineId")))) {
                it.remove();
                changed = true;
            }
        }
        if (!changed) {
            // 区间没变时节点也无需整理（节点 lineIds 只来自本文件区间）
            return false;
        }

        // 2. 按剩余区间重算相邻关系，并收集仍被引用的节点
        Map<String, Set<String>> prev = new LinkedHashMap<>();
        Map<String, Set<String>> next = new LinkedHashMap<>();
        Set<String> referenced = new LinkedHashSet<>();
        for (Feature f : fc.getFeatures()) {
            if (!(f.getGeometry() instanceof LineString)) {
                continue;
            }
            String from = str(f.getProperty("from"));
            String to = str(f.getProperty("to"));
            if (from == null || to == null) {
                continue;
            }
            referenced.add(from);
            referenced.add(to);
            next.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            prev.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
        }

        // 3. 整理节点：删掉孤立节点，去掉被删线路 id，更新 prev / next
        it = fc.getFeatures().iterator();
        while (it.hasNext()) {
            Feature f = it.next();
            if (!(f.getGeometry() instanceof Point)) {
                continue;
            }
            String id = str(f.getProperty("id"));
            if (id == null || !referenced.contains(id)) {
                it.remove();
                continue;
            }
            removeValues(f, "lineIds", removed);
            removeValues(f, "railwaySystemIds", removedSystems);
            f.setProperty("prev", new ArrayList<>(prev.getOrDefault(id, Set.of())));
            f.setProperty("next", new ArrayList<>(next.getOrDefault(id, Set.of())));
        }
        return true;
    }

    /**
     * 从节点的字符串数组属性中去掉指定的值。属性不存在时不处理。
     *
     * @param f      节点 feature
     * @param key    属性名
     * @param values 要去掉的值
     */
    private static void removeValues(Feature f, String key, Set<String> values) {
        if (values.isEmpty() || !(f.getProperty(key) instanceof List<?> list)) {
            return;
        }
        List<Object> kept = new ArrayList<>();
        for (Object o : list) {
            if (!values.contains(str(o))) {
                kept.add(o);
            }
        }
        f.setProperty(key, kept);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
