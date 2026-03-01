package com.chatbot.eval.evaluator;

import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.RunResult;

/**
 * E3: Outcome evaluator - stub in Iter0, real in Iter2.
 * Will verify side_effects against sandbox state in future iterations.
 */
public class OutcomeEvaluator implements Evaluator {

    @Override
    public String name() {
        return "outcome";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        // Iter0: always passes (stub)
        EvalResult result = EvalResult.pass(name());
        result.getDetails().put("note", "Stub evaluator - real implementation in Iter2");
        return result;
    }
}
