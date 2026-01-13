package com.company.flowable.ops;

public class PageInfo {
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;

    public PageInfo(int page, int size, long totalItems, int totalPages) {
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
