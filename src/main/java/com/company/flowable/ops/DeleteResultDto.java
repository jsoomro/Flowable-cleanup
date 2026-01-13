package com.company.flowable.ops;

public class DeleteResultDto {
    private String pid;
    private String result;
    private String error;

    public DeleteResultDto(String pid, String result, String error) {
        this.pid = pid;
        this.result = result;
        this.error = error;
    }

    public String getPid() {
        return pid;
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
