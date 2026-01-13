package com.company.flowable.ops;

public class OpsException extends RuntimeException {
    private final int status;

    public OpsException(int status, String message) {
        super(message);
        this.status = status;
    }

    public OpsException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
