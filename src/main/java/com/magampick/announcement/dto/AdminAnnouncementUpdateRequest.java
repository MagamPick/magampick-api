package com.magampick.announcement.dto;

import com.magampick.announcement.domain.NoticeTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 관리자 공지사항 부분 수정 요청 (PATCH). null 필드는 변경하지 않는다. @Size 는 null 허용 — null 일 때는 검증 생략, 값이 있을 때만 적용. */
@Schema(description = "관리자 공지사항 수정 요청 (PATCH — null 필드 미수정)")
public record AdminAnnouncementUpdateRequest(
    @Schema(description = "태그 (notice / event / update) — null 이면 미수정") NoticeTag tag,
    @Schema(description = "핀 여부 — null 이면 미수정") Boolean pinned,
    @Schema(description = "제목 (최대 200자) — null 이면 미수정") @Size(max = 200) String title,
    @Schema(description = "본문 — null 이면 미수정") String body) {}
