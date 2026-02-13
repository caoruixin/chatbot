package com.chatbot.mapper;

import com.chatbot.model.FaqDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaqDocMapper {

    List<FaqDoc> searchByEmbedding(@Param("embeddingVector") String embeddingVector,
                                   @Param("limit") int limit);

    void updateEmbedding(@Param("faqId") String faqId,
                         @Param("embeddingVector") String embeddingVector);

    List<FaqDoc> findWithoutEmbedding();
}
