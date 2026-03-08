package com.chatbot.service.tool;

import com.chatbot.config.PromptConfig;
import com.chatbot.dto.response.FaqSearchResponse;
import com.chatbot.mapper.FaqDocMapper;
import com.chatbot.model.FaqDoc;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * FAQ search tool executor.
 * Primary: Uses KimiClient to generate embeddings for the user query,
 * then searches the FAQ knowledge base via pgvector similarity search.
 * Fallback: When embedding API is unavailable, uses LLM chat to match FAQs directly.
 */
@Service
public class FaqService implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);

    private final FaqDocMapper faqDocMapper;
    private final KimiClient kimiClient;
    private final PromptConfig promptConfig;
    private final double faqScoreThreshold;

    public FaqService(FaqDocMapper faqDocMapper,
                      KimiClient kimiClient,
                      PromptConfig promptConfig,
                      @Value("${chatbot.ai.faq-score-threshold:0.75}") double faqScoreThreshold) {
        this.faqDocMapper = faqDocMapper;
        this.kimiClient = kimiClient;
        this.promptConfig = promptConfig;
        this.faqScoreThreshold = faqScoreThreshold;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        log.info("FAQ search requested: query={}", query);

        try {
            // Try embedding-based search first
            float[] queryEmbedding = kimiClient.embeddingQuery(query);

            String vectorString = floatArrayToVectorString(queryEmbedding);

            List<FaqDoc> results = faqDocMapper.searchByEmbedding(vectorString, 3);
            if (results.isEmpty()) {
                log.info("No FAQ results found for query");
                FaqSearchResponse response = new FaqSearchResponse("", "未找到相关问题", 0.0);
                return ToolResult.success(response);
            }

            FaqDoc bestMatch = results.get(0);
            double score = bestMatch.getScore() != null ? bestMatch.getScore() : 0.0;

            if (score < faqScoreThreshold) {
                log.info("FAQ best match score {} below threshold {}, treating as no match", score, faqScoreThreshold);
                FaqSearchResponse response = new FaqSearchResponse("", "未找到与用户问题相关的FAQ", 0.0);
                return ToolResult.success(response);
            }

            log.info("FAQ match found: faqId={}, score={}", bestMatch.getFaqId(), score);
            FaqSearchResponse response = new FaqSearchResponse(
                    bestMatch.getQuestion(), bestMatch.getAnswer(), score);
            return ToolResult.success(response);
        } catch (Exception e) {
            log.warn("Embedding-based FAQ search failed, falling back to LLM-based matching: {}", e.getMessage());
            return llmBasedFaqSearch(query);
        }
    }

    /**
     * Fallback: Uses Kimi chat completion to match user query against FAQ list.
     * This is used when the embedding API is unavailable.
     */
    private ToolResult llmBasedFaqSearch(String query) {
        try {
            List<FaqDoc> allFaqs = faqDocMapper.findAll();
            if (allFaqs.isEmpty()) {
                log.info("No FAQ documents in database");
                return ToolResult.success(new FaqSearchResponse("", "未找到相关问题", 0.0));
            }

            StringBuilder faqList = new StringBuilder();
            for (int i = 0; i < allFaqs.size(); i++) {
                faqList.append(String.format("%d. 问题: %s\n   答案: %s\n\n",
                        i + 1, allFaqs.get(i).getQuestion(), allFaqs.get(i).getAnswer()));
            }

            String userMessage = String.format("FAQ列表:\n%s用户问题: %s", faqList, query);

            KimiChatResponse response = kimiClient.chatCompletion(promptConfig.getFaqMatcherPrompt(),
                    List.of(new KimiMessage("user", userMessage)), 0.1);

            String content = response.getContent();
            if (content == null) {
                log.warn("LLM-based FAQ search returned null content");
                return ToolResult.success(new FaqSearchResponse("", "未找到相关问题", 0.0));
            }

            // Parse the FAQ index number from LLM response
            String trimmed = content.trim().replaceAll("[^0-9]", "");
            int matchIndex = Integer.parseInt(trimmed) - 1;

            if (matchIndex >= 0 && matchIndex < allFaqs.size()) {
                FaqDoc match = allFaqs.get(matchIndex);
                log.info("LLM-based FAQ match found: faqId={}, question={}", match.getFaqId(), match.getQuestion());
                return ToolResult.success(new FaqSearchResponse(match.getQuestion(), match.getAnswer(), 0.9));
            }

            log.info("LLM-based FAQ search: no match (LLM returned {})", content.trim());
            return ToolResult.success(new FaqSearchResponse("", "未找到相关问题", 0.0));
        } catch (Exception e) {
            log.error("LLM-based FAQ search also failed: {}", e.getMessage());
            return ToolResult.error("FAQ 搜索失败: " + e.getMessage());
        }
    }

    private String floatArrayToVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }
}
