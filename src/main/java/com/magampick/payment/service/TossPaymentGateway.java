package com.magampick.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magampick.global.exception.BusinessException;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.exception.PaymentErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 토스 샌드박스 결제 게이트웨이. @Profile("!test") — 테스트 환경에선 StubPaymentGateway 사용. */
@Slf4j
@Service
@Profile("!test")
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentGateway implements PaymentGateway {

  private final RestClient restClient;

  public TossPaymentGateway(TossProperties props) {
    String credentials =
        Base64.getEncoder()
            .encodeToString((props.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    this.restClient =
        RestClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("Authorization", "Basic " + credentials)
            .build();
  }

  @Override
  public PaymentApproval approve(PaymentCommand command) {
    Map<String, Object> body =
        Map.of(
            "paymentKey", command.paymentKey(),
            "orderId", command.idempotencyKey(),
            "amount", command.amount().longValue());
    try {
      TossPaymentResponse resp =
          restClient
              .post()
              .uri("/v1/payments/confirm")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(TossPaymentResponse.class);
      LocalDateTime approvedAt =
          resp.approvedAt() != null
              ? resp.approvedAt().withOffsetSameInstant(ZoneOffset.ofHours(9)).toLocalDateTime()
              : LocalDateTime.now();
      log.info("토스 결제 승인. paymentKey={}, amount={}", resp.paymentKey(), command.amount());
      return new PaymentApproval(resp.paymentKey(), PaymentStatus.APPROVED, approvedAt);
    } catch (RestClientResponseException e) {
      log.error("토스 결제 승인 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
  }

  @Override
  public PaymentCancellation cancel(PaymentCancellationCommand command) {
    Map<String, Object> body =
        Map.of(
            "cancelReason", command.cancelReason(),
            "cancelAmount", command.cancelAmount().longValue());
    try {
      TossPaymentResponse resp =
          restClient
              .post()
              .uri("/v1/payments/{paymentKey}/cancel", command.paymentKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(TossPaymentResponse.class);
      LocalDateTime cancelledAt =
          resp.cancels() != null && !resp.cancels().isEmpty()
              ? resp.cancels()
                  .get(resp.cancels().size() - 1)
                  .canceledAt()
                  .withOffsetSameInstant(ZoneOffset.ofHours(9))
                  .toLocalDateTime()
              : LocalDateTime.now();
      log.info("토스 결제 취소. paymentKey={}", command.paymentKey());
      return new PaymentCancellation(resp.paymentKey(), PaymentStatus.CANCELED, cancelledAt);
    } catch (RestClientResponseException e) {
      log.error("토스 환불 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new BusinessException(PaymentErrorCode.REFUND_GATEWAY_ERROR);
    }
  }

  // ── 토스 API 응답 내부 DTO ────────────────────────────────────────────────────

  private record TossPaymentResponse(
      String paymentKey,
      String method,
      @JsonProperty("approvedAt") OffsetDateTime approvedAt,
      List<TossCancelDetail> cancels) {}

  private record TossCancelDetail(
      BigDecimal cancelAmount,
      @JsonProperty("canceledAt") OffsetDateTime canceledAt,
      String cancelReason) {}
}
