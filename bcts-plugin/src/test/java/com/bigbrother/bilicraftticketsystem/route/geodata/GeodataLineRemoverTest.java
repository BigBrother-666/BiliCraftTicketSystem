package com.bigbrother.bilicraftticketsystem.route.geodata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除线路时清理 geojson 的单元测试。
 */
class GeodataLineRemoverTest {

    private static Feature point(String id, List<String> lineIds, List<String> systemIds) {
        Feature f = new Feature();
        f.setGeometry(new Point(new LngLatAlt(0, 0, 64)));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", id);
        props.put("type", "station");
        props.put("lineIds", lineIds);
        if (systemIds != null) {
            props.put("railwaySystemIds", systemIds);
        }
        f.setProperties(props);
        return f;
    }

    private static Feature line(String from, String to, String lineId) {
        Feature f = new Feature();
        f.setGeometry(new LineString(new LngLatAlt(0, 0, 64), new LngLatAlt(1, 0, 64)));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", "e." + lineId + "." + from + "__" + to);
        props.put("from", from);
        props.put("to", to);
        props.put("lineId", lineId);
        f.setProperties(props);
        return f;
    }

    private static Feature find(FeatureCollection fc, String id) {
        for (Feature f : fc.getFeatures()) {
            if (id.equals(f.getProperty("id"))) {
                return f;
            }
        }
        return null;
    }

    /**
     * L1 的文件里有一条通往被删线路 L2 的越界边：应删掉该区间、删掉只被它引用的落点节点，
     * 并更新剩余节点的 lineIds / prev / next。
     */
    @Test
    void pruneRemovesEdgesAndOrphanNodes() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("A", List.of("L1"), List.of("sys1")));
        fc.add(point("B", List.of("L1", "L2"), List.of("sys1", "sys2")));
        fc.add(point("C", List.of("L2"), List.of("sys2")));
        fc.add(line("A", "B", "L1"));
        fc.add(line("B", "C", "L2"));

        assertTrue(GeodataLineRemover.prune(fc, Set.of("L2"), Set.of("sys2")));

        assertEquals(null, find(fc, "C"), "只被删除区间引用的节点应删除");
        assertEquals(null, find(fc, "e.L2.B__C"), "被删线路的区间应删除");
        Feature b = find(fc, "B");
        assertEquals(List.of("L1"), b.getProperty("lineIds"));
        assertEquals(List.of("sys1"), b.getProperty("railwaySystemIds"));
        assertEquals(List.of(), b.getProperty("next"), "B 的后继只剩被删区间，应为空");
        assertEquals(List.of("A"), b.getProperty("prev"));
        assertEquals(List.of("B"), find(fc, "A").getProperty("next"));
    }

    /**
     * 文件中没有被删线路的区间时不改动。
     */
    @Test
    void pruneUnrelatedFileIsNoop() {
        FeatureCollection fc = new FeatureCollection();
        fc.add(point("A", List.of("L1"), null));
        fc.add(point("B", List.of("L1"), null));
        fc.add(line("A", "B", "L1"));
        assertFalse(GeodataLineRemover.prune(fc, Set.of("L2"), Set.of()));
        assertEquals(3, fc.getFeatures().size());
    }

    /**
     * 目录级：删掉被删线路自己的文件，改写含其区间的其它文件，不碰无关文件。
     */
    @Test
    void removeLinesDeletesOwnFileAndRewritesOthers(@TempDir File dir) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FeatureCollection l1 = new FeatureCollection();
        l1.add(point("A", List.of("L1"), null));
        l1.add(point("B", List.of("L1", "L2"), null));
        l1.add(line("A", "B", "L1"));
        l1.add(line("A", "B", "L2"));
        mapper.writeValue(new File(dir, "L1.geojson"), l1);

        FeatureCollection l2 = new FeatureCollection();
        l2.add(point("B", List.of("L2"), null));
        mapper.writeValue(new File(dir, "L2.geojson"), l2);

        FeatureCollection l3 = new FeatureCollection();
        l3.add(point("X", List.of("L3"), null));
        l3.add(point("Y", List.of("L3"), null));
        l3.add(line("X", "Y", "L3"));
        File l3File = new File(dir, "L3.geojson");
        mapper.writeValue(l3File, l3);
        long l3Modified = l3File.lastModified();

        GeodataLineRemover.Result result = new GeodataLineRemover().removeLines(dir, List.of("L2"), List.of());

        assertEquals(1, result.deletedFiles());
        assertEquals(1, result.rewrittenFiles());
        assertFalse(new File(dir, "L2.geojson").exists());
        FeatureCollection after = mapper.readValue(new File(dir, "L1.geojson"), FeatureCollection.class);
        assertEquals(null, find(after, "e.L2.A__B"));
        assertEquals(List.of("L1"), find(after, "B").getProperty("lineIds"));
        assertEquals(l3Modified, l3File.lastModified(), "无关文件不应被改写");
    }
}
