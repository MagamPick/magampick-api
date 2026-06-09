package com.magampick.announcement.dto;

import com.magampick.announcement.domain.NoticeTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 관리자 공지사항 생성 요청. */
@Schema(description = "관리자 공지사항 생성 요청")
public record AdminAnnouncementCreateRequest(
    @Schema(description = "태그 (notice / event / update)") @NotNull NoticeTag tag,
    @Schema(description = "핀 여부") boolean pinned,
    @Schema(description = "제목 (최대 200자)") @NotBlank @Size(max = 200) String title,
    @Schema(description = "본문") @NotBlank String body) {}
