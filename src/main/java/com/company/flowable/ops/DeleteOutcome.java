package com.company.flowable.ops;

public class DeleteOutcome {
    private final String result;
    private final String error;
    private final int attempts;
    private final long durationMillis;
    private final String classification;

    private DeleteOutcome(String result, String error) {
        this(result, error, 1, 0, null);
    }

    private DeleteOutcome(String result, String error, int attempts, long durationMillis, String classification) {
        this.result = result;
        this.error = error;
        this.attempts = attempts;
        this.durationMillis = durationMillis;
        this.classification = classification;
    }

    public static DeleteOutcome ok() {
        return new DeleteOutcome("OK", null);
    }

    public static DeleteOutcome ok(int attempts, long durationMillis) {
        return new DeleteOutcome("OK", null, attempts, durationMillis, "SUCCESS");
    }

    public static DeleteOutcome skipped(String error) {
        return new DeleteOutcome("SKIPPED", error);
    }

    public static DeleteOutcome fail(String error) {
        return new DeleteOutcome("FAIL", error);
    }

    public static DeleteOutcome fail(String error, int attempts, long durationMillis, String classification) {
        return new DeleteOutcome("FAIL", error, attempts, durationMillis, classification);
    }

    public static DeleteOutcome quarantined(String error, int attempts, long durationMillis) {
        return new DeleteOutcome("QUARANTINED", error, attempts, durationMillis, "NPE");
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public int getAttempts() {
        return attempts;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getClassification() {
        return classification;
    }
}
