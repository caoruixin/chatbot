package com.chatbot.eval.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class EvalCommand implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalCommand.class);

    private final RunCommand runCommand;
    private final CompareCommand compareCommand;
    private final ListFailuresCommand listFailuresCommand;
    private final DiscoverCommand discoverCommand;
    private final ApplicationContext applicationContext;

    public EvalCommand(RunCommand runCommand,
                       CompareCommand compareCommand,
                       ListFailuresCommand listFailuresCommand,
                       DiscoverCommand discoverCommand,
                       ApplicationContext applicationContext) {
        this.runCommand = runCommand;
        this.compareCommand = compareCommand;
        this.listFailuresCommand = listFailuresCommand;
        this.discoverCommand = discoverCommand;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        try {
            if (args.length == 0) {
                printUsage();
                return;
            }

            String command = args[0];
            switch (command) {
                case "run" -> runCommand.execute(args);
                case "compare" -> compareCommand.execute(args);
                case "list-failures" -> listFailuresCommand.execute(args);
                case "discover" -> discoverCommand.execute(args);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                }
            }
        } finally {
            // Shut down Spring context so HikariCP and other background threads don't block JVM exit
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }

    private void printUsage() {
        System.out.println("Usage: evalRun --args=\"<command> [options]\"");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  run              Run evaluation episodes");
        System.out.println("    --dataset      Path to episodes.jsonl (default: data/episodes.jsonl)");
        System.out.println("    --out          Output directory (default: results/run_<timestamp>)");
        System.out.println();
        System.out.println("  compare          Compare baseline vs candidate results");
        System.out.println("    --baseline     Path to baseline results directory");
        System.out.println("    --candidate    Path to candidate results directory");
        System.out.println("    --out          Output directory (default: results/compare_<timestamp>)");
        System.out.println();
        System.out.println("  list-failures    List failed episodes");
        System.out.println("    --results      Path to results directory");
        System.out.println("    --filter       Filter by evaluator name (e.g., contract, trajectory)");
        System.out.println();
        System.out.println("  discover         Export agent configuration discovery.json");
        System.out.println("    --out          Output path (default: discovery.json)");
    }
}
