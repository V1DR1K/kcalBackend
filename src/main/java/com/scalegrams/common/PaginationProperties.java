package com.scalegrams.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.pagination")
@Getter
@Setter
public class PaginationProperties {
    private int defaultSize = 20;
    private int maxSize = 50;

    public int normalizePage(int page) {
        return Math.max(0, page);
    }

    public int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), Math.max(maxSize, 1));
    }

    public Pageable pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size), sort);
    }
}
