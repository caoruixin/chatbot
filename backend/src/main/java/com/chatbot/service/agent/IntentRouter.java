package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Intent Router - recognizes user intent using Kimi LLM.
 * Calls Kimi with a system prompt for structured JSON intent classification.
 * Returns IntentResult with intent, confidence, risk, and extracted parameters.
 */
@Service
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private static final double INTENT_TEMPERATURE = 0.1;

    private static final String INTENT_SYSTEM_PROMPT = """
            你是一个意图识别引擎。分析用户消息，返回严格的 JSON 格式结果。

            可识别的意图类型：
            - POST_QUERY: 用户想查询、查看、了解帖子/文章的状态或内容。即使用户没有提供具体用户名也归为 POST_QUERY（系统会追问用户名）。关键词包括：查询帖子、查帖子、帖子状态、我的帖子等。
            - DATA_DELETION: 用户想删除自己的数据（需要提取 username 参数）
            - KB_QUESTION: 用户在问产品、服务、政策、功能、**操作方法**等方面的**通用问题**。包括但不限于：退款、退货、密码重置、账号安全、两步验证、支付方式、配送、会员、审核流程/时长等一切业务相关的通用知识问题。注意区分：问"帖子审核要多久"是 KB_QUESTION（问通用流程），问"查询帖子状态"是 POST_QUERY（想查具体帖子）。
            - GENERAL_CHAT: 仅限纯粹的闲聊/打招呼/无法归类的消息（如"你好"、"谢谢"、"再见"）

            风险等级：
            - "low": POST_QUERY, KB_QUESTION, GENERAL_CHAT
            - "critical": DATA_DELETION

            **重要**：当不确定时，优先归类为 KB_QUESTION 而非 GENERAL_CHAT。只有纯闲聊才归为 GENERAL_CHAT。

            请严格按以下 JSON 格式返回，不要包含任何其他文字：
            {"intent":"意图类型","confidence":0.0到1.0的数字,"risk":"low或critical","extracted_params":{"参数名":"参数值"}}

            示例：
            用户说"帮我查一下alice的帖子" → {"intent":"POST_QUERY","confidence":0.95,"risk":"low","extracted_params":{"username":"alice"}}
            用户说"查询帖子状态" → {"intent":"POST_QUERY","confidence":0.9,"risk":"low","extracted_params":{}}
            用户说"我要查询帖子的状态" → {"intent":"POST_QUERY","confidence":0.9,"risk":"low","extracted_params":{}}
            用户说"帮我查一下帖子" → {"intent":"POST_QUERY","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"我的帖子怎么样了" → {"intent":"POST_QUERY","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"我想删除我的所有数据，我的用户名是bob" → {"intent":"DATA_DELETION","confidence":0.9,"risk":"critical","extracted_params":{"username":"bob"}}
            用户说"你们的退款政策是什么" → {"intent":"KB_QUESTION","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"查询下退款政策" → {"intent":"KB_QUESTION","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"怎么重置密码" → {"intent":"KB_QUESTION","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"如何开启两步验证" → {"intent":"KB_QUESTION","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"帖子审核需要多久" → {"intent":"KB_QUESTION","confidence":0.85,"risk":"low","extracted_params":{}}
            用户说"你好" → {"intent":"GENERAL_CHAT","confidence":0.9,"risk":"low","extracted_params":{}}
            用户说"谢谢" → {"intent":"GENERAL_CHAT","confidence":0.9,"risk":"low","extracted_params":{}}
            """;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String getSystemPrompt() {
        return INTENT_SYSTEM_PROMPT;
    }

    private final KimiClient kimiClient;

    public IntentRouter(KimiClient kimiClient) {
        this.kimiClient = kimiClient;
    }

    /**
     * Recognize the user's intent from their message and conversation history.
     * Uses Kimi LLM with low temperature for deterministic classification.
     */
    public IntentResult recognize(String userMessage, List<KimiMessage> history) {
        log.info("Recognizing intent for message: length={}", userMessage.length());

        try {
            List<KimiMessage> messages = new ArrayList<>();

            // Include recent history for context
            if (history != null) {
                messages.addAll(history);
            }

            // Add the current user message
            messages.add(new KimiMessage("user", userMessage));

            KimiChatResponse response = kimiClient.chatCompletion(
                    INTENT_SYSTEM_PROMPT, messages, INTENT_TEMPERATURE);

            String content = response.getContent();
            if (content == null || content.isBlank()) {
                log.warn("Empty response from intent recognition LLM, falling back to GENERAL_CHAT");
                return fallbackIntent();
            }

            return parseIntentResponse(content);
        } catch (Exception e) {
            log.error("Intent recognition failed: {}", e.getMessage());
            return fallbackIntent();
        }
    }

    @SuppressWarnings("unchecked")
    private IntentResult parseIntentResponse(String content) {
        try {
            // Strip any markdown code fences the LLM might add
            String json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String intent = (String) parsed.get("intent");
            Number confidenceNum = (Number) parsed.get("confidence");
            double confidence = confidenceNum != null ? confidenceNum.doubleValue() : 0.0;
            String risk = (String) parsed.get("risk");

            Map<String, String> extractedParams = new HashMap<>();
            Object paramsObj = parsed.get("extracted_params");
            if (paramsObj instanceof Map) {
                Map<String, Object> rawParams = (Map<String, Object>) paramsObj;
                for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                    if (entry.getValue() != null) {
                        extractedParams.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }

            // Validate intent value
            if (!isValidIntent(intent)) {
                log.warn("Unknown intent '{}' from LLM, falling back to GENERAL_CHAT", intent);
                return fallbackIntent();
            }

            IntentResult result = new IntentResult(intent, confidence, risk, extractedParams);
            log.info("Intent recognized: intent={}, confidence={}, risk={}, params={}",
                    intent, confidence, risk, extractedParams.keySet());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse intent JSON: {}, falling back to GENERAL_CHAT", e.getMessage());
            return fallbackIntent();
        }
    }

    private boolean isValidIntent(String intent) {
        return "POST_QUERY".equals(intent)
                || "DATA_DELETION".equals(intent)
                || "KB_QUESTION".equals(intent)
                || "GENERAL_CHAT".equals(intent);
    }

    private IntentResult fallbackIntent() {
        return new IntentResult("GENERAL_CHAT", 0.3, "low", new HashMap<>());
    }
}
