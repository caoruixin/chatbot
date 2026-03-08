package com.chatbot.eval.evaluator.deprecated;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.RunResult;

/**
 * @deprecated Phase 0 stub evaluator. Replaced by LayeredOutcomeEvaluator (L2) in Phase 1.
 * Kept for reference only — do not use in new code.
 */
@Deprecated
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
