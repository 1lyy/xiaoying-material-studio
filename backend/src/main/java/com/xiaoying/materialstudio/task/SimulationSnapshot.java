package com.xiaoying.materialstudio.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.web.ApiException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record SimulationSnapshot(TaskStage failAtStage, Map<TaskStage, Integer> stageDelayMs) {
    public static SimulationSnapshot parse(JsonNode node, boolean demoMode, int defaultDelay) {
        if (defaultDelay < 0 || defaultDelay > 30000) throw new IllegalStateException("Invalid default delay");
        if (node != null && !node.isNull() && !demoMode) throw invalid();
        TaskStage fail = null;
        EnumMap<TaskStage, Integer> delays = new EnumMap<>(TaskStage.class);
        for (TaskStage stage : TaskStage.values()) delays.put(stage, defaultDelay);
        if (node != null && !node.isNull()) {
            if (!node.isObject()) throw invalid();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!field.equals("failAtStage") && !field.equals("stageDelayMs")) throw invalid();
            }
            JsonNode failNode = node.get("failAtStage");
            if (failNode != null && !failNode.isNull()) {
                if (!failNode.isTextual()) throw invalid();
                try { fail = TaskStage.valueOf(failNode.textValue()); }
                catch (IllegalArgumentException exception) { throw invalid(); }
            }
            JsonNode delayNode = node.get("stageDelayMs");
            if (delayNode != null) {
                if (!delayNode.isObject()) throw invalid();
                Iterator<Map.Entry<String, JsonNode>> entries = delayNode.fields();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    TaskStage stage;
                    try { stage = TaskStage.valueOf(entry.getKey()); }
                    catch (IllegalArgumentException exception) { throw invalid(); }
                    JsonNode value = entry.getValue();
                    if (!value.isIntegralNumber() || !value.canConvertToInt()
                            || value.intValue() < 0 || value.intValue() > 30000) throw invalid();
                    delays.put(stage, value.intValue());
                }
            }
        }
        return new SimulationSnapshot(fail, Map.copyOf(delays));
    }

    public static SimulationSnapshot fromJson(String value, ObjectMapper json) {
        try {
            JsonNode node = json.readTree(value);
            return parse(node, true, 0);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted simulation", exception);
        }
    }

    public String toJson(ObjectMapper json) {
        try { return json.writeValueAsString(this); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize simulation", exception); }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION", "模拟参数无效");
    }
}
