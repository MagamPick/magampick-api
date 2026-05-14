package com.magampick.global.response;

import java.util.List;
import org.springframework.data.domain.Slice;

/** 무한 스크롤 응답 (totalCount 없음, count 쿼리 미실행). */
public record SliceResponse<T>(List<T> content, int page, int size, boolean hasNext) {

  public static <T> SliceResponse<T> of(Slice<T> slice) {
    return new SliceResponse<>(
        slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
  }
}
