package com.chatbot.controller;

import com.chatbot.dto.ApiResponse;
import com.chatbot.dto.response.StreamTokenResponse;
import com.chatbot.service.stream.GetStreamService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stream")
public class StreamTokenController {

    private final GetStreamService getStreamService;

    public StreamTokenController(GetStreamService getStreamService) {
        this.getStreamService = getStreamService;
    }

    @GetMapping("/token")
    public ApiResponse<StreamTokenResponse> getToken(@RequestParam String userId) {
        // Ensure user exists in GetStream
        getStreamService.upsertUser(userId, userId);

        // Generate token
        String token = getStreamService.createToken(userId);
        StreamTokenResponse response = new StreamTokenResponse(token, userId);
        return ApiResponse.success(response);
    }
}
