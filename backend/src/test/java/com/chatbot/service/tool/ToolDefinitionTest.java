package com.chatbot.service.tool;

import com.chatbot.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void fromName_validName_returnsDefinition() {
        ToolDefinition def = ToolDefinition.fromName("faq_search");
        assertNotNull(def);
        assertEquals(ToolDefinition.FAQ_SEARCH, def);
    }

    @Test
    void fromName_unknownName_returnsNull() {
        ToolDefinition def = ToolDefinition.fromName("nonexistent");
        assertNull(def);
    }

    @Test
    void faqSearch_hasReadRisk() {
        assertEquals(RiskLevel.READ, ToolDefinition.FAQ_SEARCH.getRisk());
    }

    @Test
    void postQuery_hasReadRisk() {
        assertEquals(RiskLevel.READ, ToolDefinition.POST_QUERY.getRisk());
    }

    @Test
    void userDataDelete_hasIrreversibleRisk() {
        assertEquals(RiskLevel.IRREVERSIBLE, ToolDefinition.USER_DATA_DELETE.getRisk());
    }

    @Test
    void allDefinitions_haveNameAndDescription() {
        for (ToolDefinition def : ToolDefinition.values()) {
            assertNotNull(def.getName());
            assertNotNull(def.getDescription());
            assertNotNull(def.getRisk());
            assertFalse(def.getName().isEmpty());
            assertFalse(def.getDescription().isEmpty());
        }
    }
}
