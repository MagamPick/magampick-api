package com.magampick.store.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magampick.global.exception.BusinessException;
import com.magampick.store.exception.StoreErrorCode;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 국세청 사업자등록 진위확인 실연동 (test 제외 전 환경). 공공데이터포털 진위확인 API(validate) 한 번 호출로 (사업자번호·대표자명·개업일자) 일치 여부 +
 * 정상 영업 여부를 함께 판정한다.
 *
 * <p>판정 순서: 호출/응답 실패 → {@code VERIFICATION_FAILED}, status 없음·공백(미등록) 또는 {@code b_stt_cd !=
 * "01"}(휴·폐업) → {@code NOT_ACTIVE}, {@code valid != "01"}(3요소 불일치) → {@code INFO_MISMATCH}, 그 외 통과.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class RealBusinessVerificationService implements BusinessVerificationService {

  private static final String ACTIVE_STATUS_CODE = "01"; // 계속사업자
  private static final String VALID_MATCH_CODE = "01"; // 진위확인 일치

  private final NtsBusinessProperties properties;
  private final RestClient ntsRestClient;

  @Override
  public void verify(String businessNumber, String representativeName, LocalDate openDate) {
    ValidateResponse.Item item =
        firstItem(callValidate(businessNumber, representativeName, openDate));

    ValidateResponse.Status status = item.status();
    if (status == null || status.bSttCd() == null || status.bSttCd().isBlank()) {
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE); // 미등록
    }
    if (!ACTIVE_STATUS_CODE.equals(status.bSttCd())) {
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE); // 휴업·폐업
    }
    if (!VALID_MATCH_CODE.equals(item.valid())) {
      throw new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH); // 3요소 불일치
    }
    log.info("국세청 진위확인 통과. businessNumber={}", businessNumber);
  }

  private ValidateResponse callValidate(
      String businessNumber, String representativeName, LocalDate openDate) {
    ValidateRequest body =
        new ValidateRequest(
            List.of(
                new ValidateRequest.Business(
                    businessNumber,
                    openDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                    representativeName)));
    try {
      return ntsRestClient
          .post()
          .uri(URI.create(properties.validateUrl() + "?serviceKey=" + properties.serviceKey()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(ValidateResponse.class);
    } catch (RestClientException e) {
      log.warn("국세청 진위확인 호출 실패. businessNumber={}", businessNumber, e);
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_VERIFICATION_FAILED);
    }
  }

  private ValidateResponse.Item firstItem(ValidateResponse response) {
    if (response == null || response.data() == null || response.data().isEmpty()) {
      log.warn("국세청 진위확인 응답이 비어 있음");
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_VERIFICATION_FAILED);
    }
    return response.data().get(0);
  }

  /** 진위확인 요청 — businesses 배열에 (사업자번호·개업일자 yyyyMMdd·대표자명) 한 건. */
  private record ValidateRequest(List<Business> businesses) {
    private record Business(
        @JsonProperty("b_no") String bNo,
        @JsonProperty("start_dt") String startDt,
        @JsonProperty("p_nm") String pNm) {}
  }

  /** 진위확인 응답 — data[].valid(일치 여부) + data[].status.b_stt_cd(영업 상태). 사용 필드만 매핑. */
  private record ValidateResponse(List<Item> data) {
    private record Item(String valid, Status status) {}

    private record Status(@JsonProperty("b_stt_cd") String bSttCd) {}
  }
}
