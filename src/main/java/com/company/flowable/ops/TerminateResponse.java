package com.company.flowable.ops;

import java.util.ArrayList;
import java.util.List;

public class TerminateResponse {
    private List<DeleteResultDto> results = new ArrayList<>();

    public TerminateResponse(List<DeleteResultDto> results) {
        this.results = results;
    }

    public List<DeleteResultDto> getResults() {
        return results;
    }
}
