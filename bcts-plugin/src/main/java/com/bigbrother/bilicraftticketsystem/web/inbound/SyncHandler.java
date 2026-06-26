package com.bigbrother.bilicraftticketsystem.web.inbound;

import com.bigbrother.bilicraftticketsystem.web.Envelope;
import com.bigbrother.bilicraftticketsystem.web.outbound.SnapshotPublisher;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 处理后端下发的 {@code sync.request}：按 {@code what} 补推对应快照（见 docs/BACKEND_PROMPT.md §4.4）。
 */
public class SyncHandler {
    private final SnapshotPublisher publisher;

    public SyncHandler(SnapshotPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 处理一条 sync.request 信封。
     *
     * @param env 信封（data.what = geo|systems|lines|all）
     */
    public void handle(Envelope env) {
        String what = "all";
        JsonNode data = env.data;
        if (data != null && data.hasNonNull("what")) {
            what = data.get("what").asText("all");
        }
        switch (what) {
            case "geo" -> publisher.publishGeo();
            case "systems" -> publisher.publishSystems();
            case "lines" -> publisher.publishLines();
            default -> publisher.publishAll();
        }
    }
}
