package com.chatbot.mapper;

import com.chatbot.model.UserPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPostMapper {

    List<UserPost> findByUsername(@Param("username") String username);
}
