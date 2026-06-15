package com.magampick.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.magampick.global.exception.BusinessException;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.service.PhoneVerificationService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.dto.SellerPhoneUpdateRequest;
import com.magampick.seller.dto.SellerProfileResponse;
import com.magampick.seller.dto.SellerProfileUpdateRequest;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.mapper.SellerMapper;
import com.magampick.seller.repository.SellerRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

  @Mock SellerRepository sellerRepository;
  @Mock SellerMapper sellerMapper;
  @Mock PhoneVerificationService phoneVerificationService;
  @InjectMocks SellerService sellerService;

  private static final String TOKEN = "test-token-uuid";
  private static final String PHONE = "01099998888";

  private Seller activeSeller() {
    Seller seller =
        Seller.builder().email("seller@test.com").passwordHash("hash").ownerName("홍길동").build();
    ReflectionTestUtils.setField(seller, "id", 1L);
    return seller;
  }

  private SellerProfileResponse stubResponse(Seller seller) {
    return new SellerProfileResponse(
        seller.getId(),
        seller.getEmail(),
        seller.getOwnerName(),
        seller.getPhone(),
        null,
        OffsetDateTime.now());
  }

  @Test
  void 프로필_조회_성공() {
    // given
    Seller seller = activeSeller();
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
    given(sellerMapper.toProfileResponse(seller)).willReturn(stubResponse(seller));

    // when
    SellerProfileResponse response = sellerService.getProfile(1L);

    // then
    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.name()).isEqualTo("홍길동");
    verify(sellerMapper).toProfileResponse(seller);
  }

  @Test
  void 프로필_조회_실패_sellerId_미존재() {
    // given
    given(sellerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> sellerService.getProfile(999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }

  @Test
  void 프로필_조회_실패_삭제된_seller() {
    // given
    Seller seller = activeSeller();
    ReflectionTestUtils.setField(seller, "deletedAt", java.time.LocalDateTime.now());
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));

    // when / then
    assertThatThrownBy(() -> sellerService.getProfile(1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }

  @Test
  void 이름_수정_성공_갱신된_프로필_반환() {
    // given
    Seller seller = activeSeller();
    SellerProfileUpdateRequest request = new SellerProfileUpdateRequest("김철수");
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
    given(sellerMapper.toProfileResponse(any(Seller.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    SellerProfileResponse response = sellerService.updateProfile(1L, request);

    // then
    assertThat(seller.getOwnerName()).isEqualTo("김철수");
    assertThat(response.name()).isEqualTo("김철수");
    verify(sellerMapper).toProfileResponse(seller);
  }

  @Test
  void 이름_수정_실패_길이_위반() {
    // given
    Seller seller = activeSeller();
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));

    // when / then
    assertThatThrownBy(() -> sellerService.updateProfile(1L, new SellerProfileUpdateRequest("홍")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NAME_INVALID);
  }

  @Test
  void 이름_수정_실패_sellerId_미존재() {
    // given
    given(sellerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> sellerService.updateProfile(999L, new SellerProfileUpdateRequest("새이름")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }

  @Test
  void 이름_수정_실패_삭제된_seller() {
    // given
    Seller seller = activeSeller();
    ReflectionTestUtils.setField(seller, "deletedAt", java.time.LocalDateTime.now());
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));

    // when / then
    assertThatThrownBy(() -> sellerService.updateProfile(1L, new SellerProfileUpdateRequest("새이름")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }

  @Test
  void 휴대폰_변경_성공_phoneVerifiedAt_도_갱신됨() {
    // given
    Seller seller = activeSeller();
    SellerPhoneUpdateRequest request = new SellerPhoneUpdateRequest(PHONE, TOKEN);
    given(phoneVerificationService.consumeVerificationToken(TOKEN, PHONE)).willReturn(PHONE);
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
    given(sellerMapper.toProfileResponse(any(Seller.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    sellerService.updatePhone(1L, request);

    // then
    assertThat(seller.getPhone()).isEqualTo(PHONE);
    assertThat(seller.getPhoneVerifiedAt()).isNotNull();
    verify(phoneVerificationService).consumeVerificationToken(TOKEN, PHONE);
    verify(sellerMapper).toProfileResponse(seller);
  }

  @Test
  void 휴대폰_변경_실패_본인인증_토큰_만료() {
    // given
    Seller seller = activeSeller();
    SellerPhoneUpdateRequest request = new SellerPhoneUpdateRequest(PHONE, "expired-token");
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
    given(phoneVerificationService.consumeVerificationToken("expired-token", PHONE))
        .willThrow(new BusinessException(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED));

    // when / then
    assertThatThrownBy(() -> sellerService.updatePhone(1L, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED);
  }

  @Test
  void 휴대폰_변경_실패_sellerId_미존재() {
    // given
    given(sellerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> sellerService.updatePhone(999L, new SellerPhoneUpdateRequest(PHONE, TOKEN)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }

  @Test
  void 휴대폰_변경_실패_삭제된_seller() {
    // given
    Seller seller = activeSeller();
    ReflectionTestUtils.setField(seller, "deletedAt", java.time.LocalDateTime.now());
    given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));

    // when / then
    assertThatThrownBy(
            () -> sellerService.updatePhone(1L, new SellerPhoneUpdateRequest(PHONE, TOKEN)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
  }
}
