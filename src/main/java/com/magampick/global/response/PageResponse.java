package com.magampick.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** 일반 페이지네이션 응답 (totalCount 포함). */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalCount,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext(),
        page.hasPrevious());
  }
}
