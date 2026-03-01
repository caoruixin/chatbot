package com.chatbot.eval.adapter;

import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.RunResult;

import java.util.Map;

public interface AgentAdapter {

    RunResult runEpisode(Episode episode, Map<String, Object> runConfig);
}
