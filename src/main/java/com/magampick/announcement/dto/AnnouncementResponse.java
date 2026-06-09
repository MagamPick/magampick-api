package com.magampick.announcement.dto;

import com.magampick.announcement.domain.NoticeTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 공지사항 응답 DTO. date 필드는 엔티티의 publishedAt 에 매핑된다 (FE 계약 일치). */
@Schema(description = "공지사항 응답")
public record AnnouncementResponse(
    @Schema(description = "공지사항 ID") Long id,
    @Schema(description = "태그 (notice / event / update)") NoticeTag tag,
    @Schema(description = "핀 여부") boolean pinned,
    @Schema(description = "발행일 (yyyy-MM-dd)") LocalDate date,
    @Schema(description = "제목") String title,
    @Schema(description = "본문") String body) {}
