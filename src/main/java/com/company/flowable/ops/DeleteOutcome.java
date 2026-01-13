package com.company.flowable.ops;

public class DeleteOutcome {
    private final String result;
    private final String error;

    private DeleteOutcome(String result, String error) {
        this.result = result;
        this.error = error;
    }

    public static DeleteOutcome ok() {
        return new DeleteOutcome("OK", null);
    }

    public static DeleteOutcome skipped(String error) {
        return new DeleteOutcome("SKIPPED", error);
    }

    public static DeleteOutcome fail(String error) {
        return new DeleteOutcome("FAIL", error);
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
