package com.chatbot.service.tool;

import com.chatbot.mapper.FaqDocMapper;
import com.chatbot.model.FaqDoc;
import com.chatbot.service.llm.KimiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * Generates embeddings for FAQ documents that don't have them yet.
 * Runs on application startup via ApplicationRunner.
 */
@Component
public class FaqEmbeddingInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FaqEmbeddingInitializer.class);

    private final FaqDocMapper faqDocMapper;
    private final KimiClient kimiClient;

    public FaqEmbeddingInitializer(FaqDocMapper faqDocMapper, KimiClient kimiClient) {
        this.faqDocMapper = faqDocMapper;
        this.kimiClient = kimiClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<FaqDoc> docsWithoutEmbedding = faqDocMapper.findWithoutEmbedding();
        if (docsWithoutEmbedding.isEmpty()) {
            log.info("All FAQ documents already have embeddings, skipping initialization");
            return;
        }

        log.info("Found {} FAQ documents without embeddings, generating...", docsWithoutEmbedding.size());

        int successCount = 0;
        int failCount = 0;

        for (FaqDoc doc : docsWithoutEmbedding) {
            try {
                float[] embedding = kimiClient.embeddingDocument(doc.getQuestion());
                if (embedding.length == 0) {
                    log.warn("Empty embedding returned for faqId={}, question={}",
                            doc.getFaqId(), doc.getQuestion());
                    failCount++;
                    continue;
                }

                String vectorString = floatArrayToVectorString(embedding);
                faqDocMapper.updateEmbedding(doc.getFaqId().toString(), vectorString);
                successCount++;

                log.info("Embedding generated for faqId={}, dimension={}",
                        doc.getFaqId(), embedding.length);
            } catch (Exception e) {
                failCount++;
                log.error("Failed to generate embedding for faqId={}: {}",
                        doc.getFaqId(), e.getMessage());
            }
        }

        log.info("FAQ embedding initialization complete: success={}, failed={}", successCount, failCount);
    }

    private String floatArrayToVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }
}
