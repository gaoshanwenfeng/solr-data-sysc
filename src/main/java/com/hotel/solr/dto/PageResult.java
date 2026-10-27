package com.hotel.solr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;

    private int page;

    private int size;

    private long totalPages;

    private List<T> items;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> items) {
        long totalPages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(total, page, size, totalPages,
                items == null ? Collections.emptyList() : items);
    }
}
