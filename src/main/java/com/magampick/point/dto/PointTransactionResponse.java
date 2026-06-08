package com.magampick.point.dto;

import com.magampick.point.domain.PointReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 포인트 내역 단건 응답. */
@Schema(description = "포인트 내역 응답")
public record PointTransactionResponse(
    @Schema(description = "내역 ID") Long id,
    @Schema(description = "사유 (EARN/USE/EXPIRE/RESTORE/CLAWBACK)") PointReason reason,
    @Schema(description = "포인트 변동량 (양수)") long amount,
    @Schema(description = "연관 매장 이름 (없으면 null)") String storeName,
    @Schema(description = "내역 발생 시각") LocalDateTime occurredAt) {}
