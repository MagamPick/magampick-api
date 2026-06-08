package com.magampick.notification.dto;

import com.magampick.notification.domain.Notification;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 단건 알림 응답. createdAt 은 ISO-8601 + KST offset 문자열로 직렬화. */
public record NotificationResponse(
    Long id,
    String category,
    String title,
    String body,
    String createdAt,
    boolean read,
    String link) {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  public static NotificationResponse from(Notification notification) {
    String formattedAt =
        notification.getCreatedAt() == null
            ? null
            : notification.getCreatedAt().atZone(KST).format(FORMATTER);
    return new NotificationResponse(
        notification.getId(),
        notification.getCategory().name().toLowerCase(),
        notification.getTitle(),
        notification.getBody(),
        formattedAt,
        notification.isRead(),
        notification.getLink());
  }
}
