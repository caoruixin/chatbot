package com.chatbot.eval.evaluator;

import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.RunResult;

public interface Evaluator {

    String name();

    EvalResult evaluate(Episode episode, RunResult runResult);
}
