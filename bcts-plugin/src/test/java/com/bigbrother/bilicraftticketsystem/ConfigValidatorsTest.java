package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.wizard.ConfigValidators;
import com.bigbrother.bilicraftticketsystem.wizard.WizardStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorsTest {
    @Test
    void noticesRoundTrip() {
        String input = "sound:minecraft:block.note_block.bell,1,1||announce:列车即将进站";
        WizardStep.Result r = ConfigValidators.noticeList(input);
        assertTrue(r.isOk());
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) r.getValue();
        assertEquals(List.of("sound:minecraft:block.note_block.bell,1,1", "announce:列车即将进站"), list);
        assertEquals(input, ConfigValidators.joinNotices(list));
        assertEquals("", ConfigValidators.joinNotices(List.of()));
        assertTrue(ConfigValidators.noticeList("").isOk());
    }

    @Test
    void invalidNoticeRejected() {
        assertFalse(ConfigValidators.noticeList("hello").isOk());
        assertFalse(ConfigValidators.noticeList("sound:x,abc").isOk());
    }

    @Test
    void stationsValidated() {
        assertFalse(ConfigValidators.stations(List.of("A")).isOk());
        assertFalse(ConfigValidators.stations(List.of("A->B", "C")).isOk());
        WizardStep.Result r = ConfigValidators.parseStations("A -> B:RV -> C");
        assertTrue(r.isOk());
        assertEquals(List.of("A", "B:RV", "C"), r.getValue());
    }

    @Test
    void rawStationsRoundTrip() {
        LineInfo info = new LineInfo("l1", "sys", "L1", "#000000",
                List.of("A", "B", "C"), Set.of(1), null, "WHITE", null, null, "l2", "S", false);
        assertEquals(List.of("A", "B:RV", "C", "l2:S"), info.getRawBossbarStations());
        LineInfo noEntry = new LineInfo("l1", "sys", "L1", "#000000",
                List.of("A", "B"), Set.of(), null, "WHITE", null, null, "l2", "", false);
        assertEquals(List.of("A", "B", "l2"), noEntry.getRawBossbarStations());
    }

    @Test
    void colorsValidated() {
        assertTrue(ConfigValidators.hexColor("#aA00ff").isOk());
        assertFalse(ConfigValidators.hexColor("red").isOk());
        assertEquals("BLUE", ConfigValidators.bossbarColor("blue").getValue());
        assertFalse(ConfigValidators.bossbarColor("ORANGE").isOk());
    }
}
