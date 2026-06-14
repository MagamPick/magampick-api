package com.magampick.order.controller;

import com.magampick.order.dto.DevSeedOrderRequest;
import com.magampick.order.dto.DevSeedOrderResponse;
import com.magampick.order.service.DevOrderSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2E 테스트용 주문 시드 컨트롤러 — local/dev 프로파일 한정. prod 노출 금지.
 *
 * <p>토스 위젯 자동화 불가 우회: 결제 단계 없이 원하는 상태의 주문을 직접 생성.
 */
@RestController
@RequestMapping("/api/v1/dev/test")
@RequiredArgsConstructor
@Profile({"local", "dev"})
@Tag(name = "Dev Order Seed (임시)", description = "E2E 테스트용 주문 시드 엔드포인트 — local/dev 한정, 운영 노출 금지")
public class DevOrderController {

  private final DevOrderSeedService devOrderSeedService;

  @PostMapping("/orders")
  @Operation(
      summary = "[임시] 주문 시드 생성",
      description =
          "토스 결제를 우회해 targetState 까지 전이된 유효 주문을 생성한다."
              + " customerId·storeId·clearanceItemId 미지정 시 자동 선택."
              + " 허용 targetState: PENDING | PREPARING | READY | COMPLETED | NO_SHOW.")
  public DevSeedOrderResponse seedOrder(@RequestBody @Valid DevSeedOrderRequest request) {
    return devOrderSeedService.seedOrder(request);
  }
}
