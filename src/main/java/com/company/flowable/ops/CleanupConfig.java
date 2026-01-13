package com.company.flowable.ops;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class CleanupConfig implements ScannerConfig {
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPass;
    private String jdbcDriver = "oracle.jdbc.OracleDriver";
    private long hours = 6;
    private long taskEscalationHours = 6;
    private int maxPerRun = 5000;
    private Set<String> procDefKeyAllowList = new HashSet<>();
    private Set<String> procDefKeyDenyList = new HashSet<>();
    private boolean includeSubprocesses = true;
    private DeleteMode deleteMode = DeleteMode.SAFE;
    private boolean dryRun = true;
    private boolean confirm = false;
    private String confirmToken = "DELETE_SAFE";
    private String reason = "Ops cleanup";
    private int retryCount = 2;
    private long retryBackoffMillis = 500;
    private int parallelism = 1;
    private String auditFile = "flowable-cleanup-audit.jsonl";
    private String exportFile = "";
    private boolean printOnly = false;
    private String selectedFile = "";
    private String selectedPids = "";
    private boolean force = false;
    private long deletePauseMillis = 100;
    private String configFile = "";

    public static CleanupConfig fromArgs(String[] args) throws IOException {
        Map<String, String> cli = parseArgs(args);
        CleanupConfig config = new CleanupConfig();
        if (cli.containsKey("configFile")) {
            config.configFile = cli.get("configFile");
            config.loadFromProperties(Path.of(config.configFile));
        }
        config.applyMap(cli);
        return config;
    }

    private void loadFromProperties(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Config file not found: " + path);
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            props.load(fis);
        }
        Map<String, String> map = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        applyMap(map);
    }

    private void applyMap(Map<String, String> map) {
        if (map.containsKey("jdbcUrl")) {
            jdbcUrl = map.get("jdbcUrl");
        }
        if (map.containsKey("jdbcUser")) {
            jdbcUser = map.get("jdbcUser");
        }
        if (map.containsKey("jdbcPass")) {
            jdbcPass = map.get("jdbcPass");
        }
        if (map.containsKey("jdbcDriver")) {
            jdbcDriver = map.get("jdbcDriver");
        }
        if (map.containsKey("hours")) {
            hours = Long.parseLong(map.get("hours"));
        }
        if (map.containsKey("taskEscalationHours")) {
            taskEscalationHours = Long.parseLong(map.get("taskEscalationHours"));
        }
        if (map.containsKey("maxPerRun")) {
            maxPerRun = Integer.parseInt(map.get("maxPerRun"));
        }
        if (map.containsKey("procDefKeyAllowList")) {
            procDefKeyAllowList = parseSet(map.get("procDefKeyAllowList"));
        }
        if (map.containsKey("procDefKeyDenyList")) {
            procDefKeyDenyList = parseSet(map.get("procDefKeyDenyList"));
        }
        if (map.containsKey("includeSubprocesses")) {
            includeSubprocesses = Boolean.parseBoolean(map.get("includeSubprocesses"));
        }
        if (map.containsKey("deleteMode")) {
            deleteMode = DeleteMode.valueOf(map.get("deleteMode").trim().toUpperCase());
        }
        if (map.containsKey("dryRun")) {
            dryRun = Boolean.parseBoolean(map.get("dryRun"));
        }
        if (map.containsKey("confirm")) {
            confirm = Boolean.parseBoolean(map.get("confirm"));
        }
        if (map.containsKey("confirmToken")) {
            confirmToken = map.get("confirmToken");
        }
        if (map.containsKey("reason")) {
            reason = map.get("reason");
        }
        if (map.containsKey("retryCount")) {
            retryCount = Integer.parseInt(map.get("retryCount"));
        }
        if (map.containsKey("retryBackoffMillis")) {
            retryBackoffMillis = Long.parseLong(map.get("retryBackoffMillis"));
        }
        if (map.containsKey("parallelism")) {
            parallelism = Integer.parseInt(map.get("parallelism"));
        }
        if (map.containsKey("auditFile")) {
            auditFile = map.get("auditFile");
        }
        if (map.containsKey("exportFile")) {
            exportFile = map.get("exportFile");
        }
        if (map.containsKey("printOnly")) {
            printOnly = Boolean.parseBoolean(map.get("printOnly"));
        }
        if (map.containsKey("selectedFile")) {
            selectedFile = map.get("selectedFile");
        }
        if (map.containsKey("selectedPids")) {
            selectedPids = map.get("selectedPids");
        }
        if (map.containsKey("force")) {
            force = Boolean.parseBoolean(map.get("force"));
        }
        if (map.containsKey("deletePauseMillis")) {
            deletePauseMillis = Long.parseLong(map.get("deletePauseMillis"));
        }
    }

    private static Set<String> parseSet(String csv) {
        Set<String> set = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return set;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    public static List<String> readSelectedFile(String path) throws IOException {
        List<String> ids = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                ids.add(trimmed);
            }
        }
        return ids;
    }

    public Duration getCutoffDuration() {
        return Duration.ofHours(hours);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getJdbcUser() {
        return jdbcUser;
    }

    public String getJdbcPass() {
        return jdbcPass;
    }

    public String getJdbcDriver() {
        return jdbcDriver;
    }

    public long getHours() {
        return hours;
    }

    public long getTaskEscalationHours() {
        return taskEscalationHours;
    }

    public int getMaxPerRun() {
        return maxPerRun;
    }

    public Set<String> getProcDefKeyAllowList() {
        return procDefKeyAllowList;
    }

    public Set<String> getProcDefKeyDenyList() {
        return procDefKeyDenyList;
    }

    public boolean isIncludeSubprocesses() {
        return includeSubprocesses;
    }

    public DeleteMode getDeleteMode() {
        return deleteMode;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public String getConfirmToken() {
        return confirmToken;
    }

    public String getReason() {
        return reason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public int getParallelism() {
        return parallelism;
    }

    public String getAuditFile() {
        return auditFile;
    }

    public String getExportFile() {
        return exportFile;
    }

    public boolean isPrintOnly() {
        return printOnly;
    }

    public String getSelectedFile() {
        return selectedFile;
    }

    public String getSelectedPids() {
        return selectedPids;
    }

    public boolean isForce() {
        return force;
    }

    public long getDeletePauseMillis() {
        return deletePauseMillis;
    }

    public String getConfigFile() {
        return configFile;
    }
}
