package com.company.flowable.ops;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanupCli {
    private static Logger logger;

    public static void main(String[] args) throws Exception {
        CleanupConfig config = CleanupConfig.fromArgs(args);
        if (config.getAuditFile() != null && !config.getAuditFile().isEmpty()) {
            System.setProperty("ops.cleanup.audit.file", config.getAuditFile());
        }
        logger = LoggerFactory.getLogger(CleanupCli.class);
        if (config.getJdbcUrl() == null || config.getJdbcUser() == null || config.getJdbcPass() == null) {
            printUsage();
            return;
        }

        StandaloneProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration();
        cfg.setJdbcUrl(config.getJdbcUrl());
        cfg.setJdbcUsername(config.getJdbcUser());
        cfg.setJdbcPassword(config.getJdbcPass());
        cfg.setJdbcDriver(config.getJdbcDriver());
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        cfg.setAsyncExecutorActivate(false);
        cfg.setAsyncHistoryExecutorActivate(false);

        ProcessEngine engine = cfg.buildProcessEngine();
        RuntimeService runtimeService = engine.getRuntimeService();
        HistoryService historyService = engine.getHistoryService();
        TaskService taskService = engine.getTaskService();
        ManagementService managementService = engine.getManagementService();
        IdentityService identityService = engine.getIdentityService();

        Instant now = Instant.now();
        Instant cutoff = now.minus(config.getCutoffDuration());
        String runId = UUID.randomUUID().toString();

        try (AuditLogger auditLogger = new AuditLogger(config.getAuditFile(), runId)) {
            JobCountStrategy jobCountStrategy = new ApiOnlyJobCountStrategy(managementService);
            CleanupScanner scanner = new CleanupScanner(config, runtimeService, historyService, taskService, managementService, identityService, jobCountStrategy);
            CleanupClassifier classifier = new CleanupClassifier();

            List<Candidate> candidates;
            if (config.getDeleteMode() == DeleteMode.SELECTED) {
                List<String> ids = new ArrayList<>();
                if (!config.getSelectedFile().isEmpty()) {
                    ids.addAll(CleanupConfig.readSelectedFile(config.getSelectedFile()));
                }
                if (!config.getSelectedPids().isEmpty()) {
                    for (String pid : config.getSelectedPids().split(",")) {
                        if (!pid.trim().isEmpty()) {
                            ids.add(pid.trim());
                        }
                    }
                }
                if (ids.isEmpty()) {
                    logger.warn("No selected process instance IDs provided.");
                    return;
                }
                candidates = scanner.scanSelected(ids, cutoff, now, config.isForce());
            } else {
                candidates = scanner.scan(cutoff, now);
            }

            for (Candidate candidate : candidates) {
                ClassificationResult result = classifier.classify(candidate, config);
                candidate.setClassification(result.getClassification());
                candidate.setRecommendedAction(result.getRecommendedAction());
                auditLogger.logEvaluation(candidate, result);
            }

            Summary summary = summarize(candidates);
            printSummary(summary, candidates);

            if (!config.getExportFile().isEmpty()) {
                exportCsv(config.getExportFile(), candidates);
                logger.info("Exported candidates to {}", config.getExportFile());
            }

            if (config.isPrintOnly()) {
                return;
            }

            if (!config.isConfirm() || !confirmTokenValid(config)) {
                logger.warn("Deletion confirmation not satisfied. Run with --confirm=true and correct --confirmToken.");
                return;
            }

            if (config.isDryRun()) {
                logger.warn("Dry run enabled. Re-run with --dryRun=false to delete.");
                return;
            }

            if (!interactivePrompt(summary, config)) {
                logger.info("Deletion aborted by user.");
                return;
            }

            CleanupExecutor executor = new CleanupExecutor(config, runtimeService, taskService, managementService, auditLogger);
            executor.execute(candidates, now);
        } finally {
            engine.close();
        }
    }

    private static boolean confirmTokenValid(CleanupConfig config) {
        String token = config.getConfirmToken();
        if (config.getDeleteMode() == DeleteMode.ALL) {
            return "DELETE_ALL".equalsIgnoreCase(token);
        }
        if (config.getDeleteMode() == DeleteMode.SELECTED) {
            return "DELETE_SELECTED".equalsIgnoreCase(token) || "DELETE_SAFE".equalsIgnoreCase(token);
        }
        return "DELETE_SAFE".equalsIgnoreCase(token);
    }

    private static boolean interactivePrompt(Summary summary, CleanupConfig config) throws IOException {
        ConsolePrompt prompt = new ConsolePrompt();
        if (config.getDeleteMode() == DeleteMode.ALL) {
            String answer = prompt.ask("Delete ALL matching instances now? (yes/no)");
            if (!"yes".equalsIgnoreCase(answer)) {
                return false;
            }
            String token = prompt.ask("Type DELETE_ALL to confirm");
            return "DELETE_ALL".equalsIgnoreCase(token);
        }

        if (config.getDeleteMode() == DeleteMode.SELECTED) {
            String answer = prompt.ask("Delete SELECTED instances now? (yes/no)");
            if (!"yes".equalsIgnoreCase(answer)) {
                return false;
            }
            String token = prompt.ask("Type DELETE_SELECTED to confirm");
            return "DELETE_SELECTED".equalsIgnoreCase(token) || "DELETE_SAFE".equalsIgnoreCase(token);
        }

        String answer = prompt.ask("Delete SAFE_TO_DELETE now? (yes/no)");
        if (!"yes".equalsIgnoreCase(answer)) {
            return false;
        }
        String token = prompt.ask("Type DELETE_SAFE to confirm");
        if (!"DELETE_SAFE".equalsIgnoreCase(token)) {
            return false;
        }

        String choice = prompt.ask("Select action: 1) delete all TERMINATE 2) export list 3) exit");
        if ("2".equals(choice.trim())) {
            if (config.getExportFile() == null || config.getExportFile().isEmpty()) {
                String exportPath = prompt.ask("Enter export file path");
                if (exportPath != null && !exportPath.trim().isEmpty()) {
                    exportCsv(exportPath.trim(), summary.candidates);
                    logger.info("Exported candidates to {}", exportPath.trim());
                }
            }
            return false;
        }
        return "1".equals(choice.trim());
    }

    private static void exportCsv(String path, List<Candidate> candidates) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("pid,procDefKey,startTime,hoursRunning,classification,recommendedAction,openTasksCount,oldestTaskAge,timerCount,overdueJobCount,overdueTimerCount,starterUserId,starterEmail,isSubprocess,parentPid");
            writer.newLine();
            for (Candidate c : candidates) {
                writer.write(csv(c.getProcessInstanceId()));
                writer.write(',');
                writer.write(csv(c.getProcessDefinitionKey()));
                writer.write(',');
                writer.write(csv(c.getStartTime() == null ? "" : c.getStartTime().toString()));
                writer.write(',');
                writer.write(String.valueOf(c.getHoursRunning()));
                writer.write(',');
                writer.write(csv(c.getClassification() == null ? "" : c.getClassification().name()));
                writer.write(',');
                writer.write(csv(c.getRecommendedAction() == null ? "" : c.getRecommendedAction().name()));
                writer.write(',');
                writer.write(String.valueOf(c.getOpenTasksCount()));
                writer.write(',');
                writer.write(String.valueOf(c.getOldestTaskAgeHours() == null ? "" : c.getOldestTaskAgeHours()));
                writer.write(',');
                writer.write(String.valueOf(c.getTimerCount()));
                writer.write(',');
                writer.write(String.valueOf(c.getOverdueJobCount()));
                writer.write(',');
                writer.write(String.valueOf(c.getOverdueTimerCount()));
                writer.write(',');
                writer.write(csv(c.getStarterUserId()));
                writer.write(',');
                writer.write(csv(c.getStarterEmail()));
                writer.write(',');
                writer.write(String.valueOf(c.isSubprocess()));
                writer.write(',');
                writer.write(csv(c.getParentPid()));
                writer.newLine();
            }
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static Summary summarize(List<Candidate> candidates) {
        Summary summary = new Summary(candidates);
        for (Candidate c : candidates) {
            summary.total++;
            if (c.getClassification() == Classification.SAFE_TO_DELETE) {
                summary.safe++;
            } else {
                summary.review++;
            }
            summary.byAction.merge(c.getRecommendedAction(), 1, Integer::sum);
        }
        return summary;
    }

    private static void printSummary(Summary summary, List<Candidate> candidates) {
        logger.info("Total candidates: {}", summary.total);
        logger.info("SAFE_TO_DELETE: {}", summary.safe);
        logger.info("REVIEW_ONLY: {}", summary.review);
        for (Map.Entry<RecommendedAction, Integer> entry : summary.byAction.entrySet()) {
            logger.info("{}: {}", entry.getKey(), entry.getValue());
        }

        printTop("SAFE_TO_DELETE", candidates, c -> c.getClassification() == Classification.SAFE_TO_DELETE);
        printTop("REVIEW_ONLY", candidates, c -> c.getClassification() == Classification.REVIEW_ONLY);
    }

    private static void printTop(String title, List<Candidate> candidates, java.util.function.Predicate<Candidate> filter) {
        logger.info("Top {}:", title);
        candidates.stream()
            .filter(filter)
            .sorted(Comparator.comparingLong(Candidate::getHoursRunning).reversed())
            .limit(20)
            .forEach(c -> logger.info("pid={} key={} hoursRunning={} starter={} tasks={} oldestTask={} timerCount={} overdueJobs={} overdueTimers={} activeActivityIds={}",
                c.getProcessInstanceId(),
                c.getProcessDefinitionKey(),
                c.getHoursRunning(),
                c.getStarterUserId(),
                c.getOpenTasksCount(),
                c.getOldestTaskAgeHours(),
                c.getTimerCount(),
                c.getOverdueJobCount(),
                c.getOverdueTimerCount(),
                truncateList(c.getActiveActivityIds())));
    }

    private static String truncateList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        int max = Math.min(10, list.size());
        List<String> subset = list.subList(0, max);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < subset.size(); i++) {
            sb.append(subset.get(i));
            if (i < subset.size() - 1) {
                sb.append(",");
            }
        }
        if (list.size() > max) {
            sb.append(",...truncated");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void printUsage() {
        logger.info("Usage: java -jar flowable-cleanup.jar --jdbcUrl=... --jdbcUser=... --jdbcPass=... [options]");
    }

    private static class Summary {
        int total;
        int safe;
        int review;
        Map<RecommendedAction, Integer> byAction = new HashMap<>();
        List<Candidate> candidates;

        Summary(List<Candidate> candidates) {
            this.candidates = candidates;
        }
    }
}
