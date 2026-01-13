package com.company.flowable.ops;

public class ApiError {
    private String message;
    private String detail;

    public ApiError(String message, String detail) {
        this.message = message;
        this.detail = detail;
    }

    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }
}
