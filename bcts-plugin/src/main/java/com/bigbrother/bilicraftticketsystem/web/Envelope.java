package com.bigbrother.bilicraftticketsystem.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 插件 ↔ 后端 WebSocket 消息信封（见 docs/BACKEND_PROMPT.md §4.2）。
 * <pre>
 * { "type": "...", "id": "...", "ts": 1719100000000, "data": { ... } }
 * </pre>
 * {@code id} 用于请求 / 应答关联（如购票），可空。{@code data} 为各 type 的负载。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Envelope {
    /**
     * 共享 ObjectMapper 单例（Jackson 线程安全）。
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public String type;
    public String id;
    public long ts;
    public JsonNode data;

    public Envelope(String type, String id, long ts, JsonNode data) {
        this.type = type;
        this.id = id;
        this.ts = ts;
        this.data = data;
    }

    /**
     * 构造一条无关联 id 的消息（ts 取当前时间）。
     *
     * @param type 消息类型
     * @param data 负载（可为 null）
     * @return 信封
     */
    public static Envelope of(String type, JsonNode data) {
        return new Envelope(type, null, System.currentTimeMillis(), data);
    }

    /**
     * 构造一条带关联 id 的消息（ts 取当前时间）。
     *
     * @param type 消息类型
     * @param id   关联 id
     * @param data 负载（可为 null）
     * @return 信封
     */
    public static Envelope of(String type, String id, JsonNode data) {
        return new Envelope(type, id, System.currentTimeMillis(), data);
    }

    /**
     * 序列化为 JSON 文本。
     *
     * @return JSON 字符串
     * @throws com.fasterxml.jackson.core.JsonProcessingException 序列化失败
     */
    public String encode() throws com.fasterxml.jackson.core.JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }

    /**
     * 从 JSON 文本解析信封。
     *
     * @param json JSON 字符串
     * @return 信封
     * @throws com.fasterxml.jackson.core.JsonProcessingException 解析失败
     */
    public static Envelope decode(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        return MAPPER.readValue(json, Envelope.class);
    }

    /**
     * 新建一个空对象节点，便于构建负载。
     *
     * @return 空 ObjectNode
     */
    public static ObjectNode newData() {
        return MAPPER.createObjectNode();
    }
}
