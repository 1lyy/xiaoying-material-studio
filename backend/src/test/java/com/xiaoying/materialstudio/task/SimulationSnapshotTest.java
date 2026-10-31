package com.xiaoying.materialstudio.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.web.ApiException;
import org.junit.jupiter.api.Test;

class SimulationSnapshotTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void snapshotsDefaultsAndRejectsInvalidOverrides() throws Exception {
        SimulationSnapshot defaults = SimulationSnapshot.parse(null, false, 2000);
        assertNull(defaults.failAtStage());
        assertEquals(2000, defaults.stageDelayMs().get(TaskStage.ASSET_CHECK));
        assertEquals(defaults, SimulationSnapshot.parse(json.readTree("null"), false, 2000));
        SimulationSnapshot override = SimulationSnapshot.parse(json.readTree("""
                {"failAtStage":"GENERATE","stageDelayMs":{"GENERATE":0}}
                """), true, 2000);
        assertEquals(TaskStage.GENERATE, override.failAtStage());
        assertEquals(0, override.stageDelayMs().get(TaskStage.GENERATE));
        assertEquals(2000, override.stageDelayMs().get(TaskStage.FINALIZE));
        assertEquals(override, SimulationSnapshot.fromJson(override.toJson(json), json));
        for (String invalid : new String[] {
                "{\"failAtStage\":\"OTHER\"}",
                "{\"stageDelayMs\":{\"GENERATE\":-1}}",
                "{\"stageDelayMs\":{\"GENERATE\":30001}}",
                "{\"stageDelayMs\":{\"GENERATE\":1.5}}",
                "{\"stageDelayMs\":{\"OTHER\":1}}",
                "{\"randomFailure\":true}"
        }) {
            assertEquals("INVALID_SIMULATION", assertThrows(ApiException.class,
                    () -> SimulationSnapshot.parse(json.readTree(invalid), true, 2000)).code());
        }
        assertThrows(ApiException.class, () -> SimulationSnapshot.parse(json.readTree("{}"), false, 2000));
    }
}
