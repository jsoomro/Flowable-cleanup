package com.company.flowable.ops;

import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private PageInfo page;
    private SummaryCounts summaryCounts;

    public PageResult(List<T> items, PageInfo page, SummaryCounts summaryCounts) {
        this.items = items;
        this.page = page;
        this.summaryCounts = summaryCounts;
    }

    public List<T> getItems() {
        return items;
    }

    public PageInfo getPage() {
        return page;
    }

    public SummaryCounts getSummaryCounts() {
        return summaryCounts;
    }
}
