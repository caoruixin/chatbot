package com.chatbot.service.tool;

import com.chatbot.dto.response.FaqSearchResponse;
import com.chatbot.mapper.FaqDocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FAQ search tool executor.
 * Phase 1: Returns empty results (embedding not yet implemented).
 * Phase 2: Will use KimiClient to generate embeddings and search via pgvector.
 */
@Service
public class FaqService implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);

    private final FaqDocMapper faqDocMapper;

    public FaqService(FaqDocMapper faqDocMapper) {
        this.faqDocMapper = faqDocMapper;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        log.info("FAQ search requested: query={}", query);

        // Phase 1: Return placeholder - embedding search not yet implemented
        // Phase 2: Generate embedding via KimiClient, search via pgvector
        FaqSearchResponse response = new FaqSearchResponse();
        response.setQuestion("");
        response.setAnswer("FAQ 搜索功能将在 Phase 2 实现");
        response.setScore(0.0);

        return ToolResult.success(response);
    }
}
