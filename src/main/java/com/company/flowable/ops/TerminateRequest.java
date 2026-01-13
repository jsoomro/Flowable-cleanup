package com.company.flowable.ops;

import java.util.ArrayList;
import java.util.List;

public class TerminateRequest {
    private List<String> pids = new ArrayList<>();
    private String reason;
    private String token;
    private boolean verify = true;

    public List<String> getPids() {
        return pids;
    }

    public void setPids(List<String> pids) {
        this.pids = pids;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isVerify() {
        return verify;
    }

    public void setVerify(boolean verify) {
        this.verify = verify;
    }
}
