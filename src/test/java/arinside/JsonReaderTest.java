package arinside;

import arinside.util.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReaderTest {

    @Test
    void primitives() {
        assertEquals("hi", JsonReader.parse("\"hi\""));
        assertEquals(42L, JsonReader.parse("42"));
        assertEquals(-3L, JsonReader.parse("-3"));
        assertEquals(1.5, JsonReader.parse("1.5"));
        assertEquals(1000.0, JsonReader.parse("1e3"));
        assertEquals(Boolean.TRUE, JsonReader.parse("true"));
        assertNull(JsonReader.parse("null"));
    }

    @Test
    void nestedObjectAndArray() {
        Object o = JsonReader.parse("""
            { "a": 1, "b": ["x", {"c": true}, null], "d": {"e": "f\\n\\"g\\""} }
            """);
        assertInstanceOf(Map.class, o);
        assertEquals(1L, JsonReader.lng(o, "a"));
        List<Object> b = JsonReader.asList(JsonReader.at(o, "b"));
        assertEquals(3, b.size());
        assertEquals("x", b.get(0));
        assertTrue(JsonReader.bool(b.get(1), "c"));
        assertNull(b.get(2));
        assertEquals("f\n\"g\"", JsonReader.str(o, "d", "e"));
    }

    @Test
    void navigationHelpersAreNullSafe() {
        Object o = JsonReader.parse("{\"x\":{\"y\":\"z\"}}");
        assertEquals("z", JsonReader.str(o, "x", "y"));
        assertNull(JsonReader.at(o, "x", "nope", "deeper"));
        assertNull(JsonReader.str(o, "missing"));
        assertFalse(JsonReader.bool(o, "x", "y"));           // "z" is not a boolean
        assertEquals(0L, JsonReader.lng(null, "anything"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(RuntimeException.class, () -> JsonReader.parse("{"));
        assertThrows(RuntimeException.class, () -> JsonReader.parse("{\"a\":1,}"));
        assertThrows(RuntimeException.class, () -> JsonReader.parse("[1 2]"));
        assertThrows(RuntimeException.class, () -> JsonReader.parse("nul"));
        assertThrows(RuntimeException.class, () -> JsonReader.parse("1 2"));
    }

    @Test
    void parsesAnInnovationStudioRulePayloadShape() {
        // the shape captured live from the test server
        String rule = """
            { "name": "ClearChatContextRule", "isEnabled": true, "overlayGroupId": "0",
              "triggerEvent": { "resourceType": "com.bmc.arsys.rx.services.rule.domain.TimerTriggerEvent",
                "timeCriteria": { "minutes": 5 } },
              "recordDefinitionNames": ["Cognitive Service Chat Context"],
              "qualification": { "expression": "${ruleContext.Status} = \\"Active\\"" },
              "actions": [ { "resourceType": "com.bmc.arsys.rx.services.rule.domain.CustomRuleAction",
                "name": "Clear Chat Context If Idle", "actionTypeName": "clearChatContextIfIdle",
                "inputMap": [ { "assignTarget": "chatId", "expression": "${ruleContext.Chat ID}" } ] } ] }
            """;
        Object r = JsonReader.parse(rule);
        assertEquals("ClearChatContextRule", JsonReader.str(r, "name"));
        assertTrue(JsonReader.bool(r, "isEnabled"));
        assertEquals("com.bmc.arsys.rx.services.rule.domain.TimerTriggerEvent",
            JsonReader.str(r, "triggerEvent", "resourceType"));
        assertEquals(5L, JsonReader.lng(r, "triggerEvent", "timeCriteria", "minutes"));
        assertEquals("Cognitive Service Chat Context",
            JsonReader.asList(JsonReader.at(r, "recordDefinitionNames")).get(0));
        assertEquals("${ruleContext.Status} = \"Active\"", JsonReader.str(r, "qualification", "expression"));
        List<Object> actions = JsonReader.asList(JsonReader.at(r, "actions"));
        assertEquals("clearChatContextIfIdle", JsonReader.str(actions.get(0), "actionTypeName"));
    }
}
