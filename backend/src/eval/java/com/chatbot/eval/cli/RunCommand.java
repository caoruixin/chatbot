package com.chatbot.eval.cli;

import com.chatbot.eval.adapter.AgentAdapter;
import com.chatbot.eval.adapter.EvalToolDispatcher;
import com.chatbot.eval.adapter.SyncAgentAdapter;
import com.chatbot.eval.evaluator.*;
import com.chatbot.eval.fingerprint.FingerprintGenerator;
import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.EvalSummary;
import com.chatbot.eval.model.VersionFingerprint;
import com.chatbot.eval.report.HtmlReportGenerator;
import com.chatbot.eval.runner.DatasetLoader;
import com.chatbot.eval.runner.EvalRunner;
import com.chatbot.eval.runner.ResultWriter;
import com.chatbot.config.KimiConfig;
import com.chatbot.service.agent.IntentRouter;
import com.chatbot.service.agent.ReactPlanner;
import com.chatbot.service.agent.ResponseComposer;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.tool.FaqService;
import com.chatbot.service.tool.PostQueryService;
import com.chatbot.service.tool.ToolDefinition;
import com.chatbot.service.tool.ToolExecutor;
import com.chatbot.service.tool.UserDataDeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RunCommand {

    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

    private final IntentRouter intentRouter;
    private final ReactPlanner reactPlanner;
    private final ResponseComposer responseComposer;
    private final FaqService faqService;
    private final PostQueryService postQueryService;
    private final UserDataDeletionService userDataDeletionService;
    private final KimiConfig kimiConfig;
    private final KimiClient kimiClient;
    private final int maxReactRounds;
    private final double confidenceThreshold;
    private final boolean autoConfirmIrreversible;
    private final long latencyThresholdMs;
    private final int maxToolCallCount;
    private final double semanticSimilarityThreshold;
    private final double judgeScoreThreshold;
    private final String judgeModelId;
    private final double judgeWeightCorrectness;
    private final double judgeWeightCompleteness;
    private final double judgeWeightTone;
    private final String semanticMode;

    public RunCommand(IntentRouter intentRouter,
                      ReactPlanner reactPlanner,
                      ResponseComposer responseComposer,
                      FaqService faqService,
                      PostQueryService postQueryService,
                      UserDataDeletionService userDataDeletionService,
                      KimiConfig kimiConfig,
                      KimiClient kimiClient,
                      @Value("${chatbot.ai.max-react-rounds:3}") int maxReactRounds,
                      @Value("${chatbot.ai.confidence-threshold:0.7}") double confidenceThreshold,
                      @Value("${chatbot.eval.auto-confirm-irreversible:true}") boolean autoConfirmIrreversible,
                      @Value("${chatbot.eval.latency-threshold-ms:15000}") long latencyThresholdMs,
                      @Value("${chatbot.eval.max-tool-call-count:5}") int maxToolCallCount,
                      @Value("${chatbot.eval.semantic-similarity-threshold:0.75}") double semanticSimilarityThreshold,
                      @Value("${chatbot.eval.judge-score-threshold:3.5}") double judgeScoreThreshold,
                      @Value("${chatbot.eval.judge-model-id:moonshot-v1-32k}") String judgeModelId,
                      @Value("${chatbot.eval.judge-weights.correctness:0.5}") double judgeWeightCorrectness,
                      @Value("${chatbot.eval.judge-weights.completeness:0.3}") double judgeWeightCompleteness,
                      @Value("${chatbot.eval.judge-weights.tone:0.2}") double judgeWeightTone,
                      @Value("${chatbot.eval.semantic-mode:mock}") String semanticMode) {
        this.intentRouter = intentRouter;
        this.reactPlanner = reactPlanner;
        this.responseComposer = responseComposer;
        this.faqService = faqService;
        this.postQueryService = postQueryService;
        this.userDataDeletionService = userDataDeletionService;
        this.kimiConfig = kimiConfig;
        this.kimiClient = kimiClient;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
        this.autoConfirmIrreversible = autoConfirmIrreversible;
        this.latencyThresholdMs = latencyThresholdMs;
        this.maxToolCallCount = maxToolCallCount;
        this.semanticSimilarityThreshold = semanticSimilarityThreshold;
        this.judgeScoreThreshold = judgeScoreThreshold;
        this.judgeModelId = judgeModelId;
        this.judgeWeightCorrectness = judgeWeightCorrectness;
        this.judgeWeightCompleteness = judgeWeightCompleteness;
        this.judgeWeightTone = judgeWeightTone;
        this.semanticMode = semanticMode;
    }

    public void execute(String[] args) {
        String datasetPath = getArg(args, "--dataset", "data/episodes.jsonl");
        String outputDir = getArg(args, "--out", "results/run_" + System.currentTimeMillis());

        log.info("Eval run: dataset={}, output={}", datasetPath, outputDir);

        try {
            // Load dataset
            DatasetLoader loader = new DatasetLoader();
            List<Episode> episodes = loader.load(datasetPath);

            if (episodes.isEmpty()) {
                System.out.println("No episodes found in dataset: " + datasetPath);
                return;
            }

            // Build tool dispatcher
            Map<String, ToolExecutor> executors = new HashMap<>();
            executors.put(ToolDefinition.FAQ_SEARCH.getName(), faqService);
            executors.put(ToolDefinition.POST_QUERY.getName(), postQueryService);
            executors.put(ToolDefinition.USER_DATA_DELETE.getName(), userDataDeletionService);

            EvalToolDispatcher evalDispatcher = new EvalToolDispatcher(executors, autoConfirmIrreversible);

            // Build adapter
            AgentAdapter adapter = new SyncAgentAdapter(
                    intentRouter, reactPlanner, responseComposer,
                    evalDispatcher, maxReactRounds, confidenceThreshold);

            // Build evaluators
            Map<String, Double> judgeWeights = Map.of(
                    "correctness", judgeWeightCorrectness,
                    "completeness", judgeWeightCompleteness,
                    "tone", judgeWeightTone);

            boolean isMockMode = "mock".equalsIgnoreCase(semanticMode);
            log.info("Semantic evaluation mode: {} (mock={})", semanticMode, isMockMode);

            List<Evaluator> evaluators = List.of(
                    new ContractEvaluator(),
                    new TrajectoryEvaluator(maxToolCallCount),
                    new EfficiencyEvaluator(latencyThresholdMs),
                    new OutcomeEvaluator(),
                    new SemanticEvaluator(kimiClient, judgeModelId,
                            semanticSimilarityThreshold, judgeScoreThreshold, judgeWeights, isMockMode),
                    new RagQualityEvaluator(kimiClient, isMockMode));

            // Generate fingerprint
            FingerprintGenerator fpGen = new FingerprintGenerator(
                    kimiConfig, maxReactRounds, confidenceThreshold);
            VersionFingerprint fingerprint = fpGen.generate();

            // Run evaluation
            ResultWriter resultWriter = new ResultWriter();
            HtmlReportGenerator htmlReportGenerator = new HtmlReportGenerator();
            EvalRunner runner = new EvalRunner(adapter, evaluators, resultWriter, htmlReportGenerator);

            EvalSummary summary = runner.run(episodes, Map.of(), fingerprint, outputDir);

            // Print summary
            System.out.println("\n=== Eval Run Complete ===");
            System.out.printf("Total: %d | Pass: %d | Fail: %d | Rate: %.0f%%%n",
                    summary.getTotalEpisodes(), summary.getTotalPass(),
                    summary.getTotalFail(), summary.getOverallPassRate() * 100);
            System.out.println("Results: " + outputDir);
            System.out.println("Report: " + outputDir + "/report.html");
            System.out.println("Semantic mode: " + semanticMode);

        } catch (Exception e) {
            log.error("Eval run failed: {}", e.getMessage(), e);
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
