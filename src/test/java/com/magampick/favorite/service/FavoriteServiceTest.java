package com.magampick.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
import com.magampick.favorite.fixture.FavoriteFixture;
import com.magampick.favorite.mapper.FavoriteMapper;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

  @Mock FavoriteRepository favoriteRepository;
  @Mock StoreRepository storeRepository;
  @Mock CustomerRepository customerRepository;
  @Mock FavoriteMapper favoriteMapper;
  @InjectMocks FavoriteService favoriteService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long STORE_ID = 10L;

  private Customer customer() {
    Customer c =
        Customer.builder()
            .email("test@example.com")
            .passwordHash("hash")
            .nickname("테스터")
            .phone("01012345678")
            .build();
    ReflectionTestUtils.setField(c, "id", CUSTOMER_ID);
    return c;
  }

  private Store approvedStore() {
    Store s =
        Store.builder()
            .seller(null)
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(null)
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .status(StoreStatus.APPROVED)
            .build();
    ReflectionTestUtils.setField(s, "id", STORE_ID);
    return s;
  }

  private Store pendingStore() {
    Store s =
        Store.builder()
            .seller(null)
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(null)
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .status(StoreStatus.PENDING)
            .build();
    ReflectionTestUtils.setField(s, "id", STORE_ID);
    return s;
  }

  // ── 즐겨찾기 등록 ────────────────────────────────────────────────────────────

  @Test
  void 즐겨찾기_등록_성공() {
    // given
    Store store = approvedStore();
    Customer customer = customer();
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.empty());
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(customer);
    given(favoriteRepository.save(any(Favorite.class))).willAnswer(inv -> inv.getArgument(0));
    FavoriteAddResponse expected = FavoriteFixture.aAddResponse(STORE_ID);
    given(favoriteMapper.toAddResponse(any())).willReturn(expected);

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    then(favoriteRepository).should().save(any(Favorite.class));
  }

  @Test
  void 이미_즐겨찾기된_경우_멱등_처리() {
    // given
    Store store = approvedStore();
    Customer customer = customer();
    Favorite existing = FavoriteFixture.aFavorite(customer, store);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.of(existing));
    given(favoriteMapper.toAddResponse(existing))
        .willReturn(FavoriteFixture.aAddResponse(STORE_ID));

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    then(favoriteRepository).should(never()).save(any());
  }

  @Test
  void 미승인_매장_즐겨찾기_등록_실패_STORE_NOT_APPROVED() {
    // given
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(pendingStore()));

    // when / then
    assertThatThrownBy(() -> favoriteService.addFavorite(CUSTOMER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_NOT_APPROVED);
    then(favoriteRepository).should(never()).save(any());
  }

  @Test
  void 존재하지_않는_매장_즐겨찾기_등록_실패_STORE_NOT_FOUND() {
    // given
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> favoriteService.addFavorite(CUSTOMER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_NOT_FOUND);
    then(favoriteRepository).should(never()).save(any());
  }

  // ── 즐겨찾기 해제 ────────────────────────────────────────────────────────────

  @Test
  void 즐겨찾기_해제_성공() {
    // when
    favoriteService.removeFavorite(CUSTOMER_ID, STORE_ID);

    // then
    then(favoriteRepository).should().deleteByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID);
  }

  @Test
  void 미등록_매장_해제_멱등_처리() {
    // deleteByCustomerIdAndStoreId는 row 없어도 예외 없이 no-op
    // when
    favoriteService.removeFavorite(CUSTOMER_ID, STORE_ID);

    // then — 예외 없이 정상 완료
    then(favoriteRepository).should().deleteByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID);
  }

  // ── 즐겨찾기 목록 조회 ───────────────────────────────────────────────────────

  @Test
  void 즐겨찾기_목록_조회_성공() {
    // given
    PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
    Store store = approvedStore();
    Customer customer = customer();
    Favorite favorite = FavoriteFixture.aFavorite(customer, store);
    Page<Favorite> page = new PageImpl<>(List.of(favorite), pageable, 1L);
    given(favoriteRepository.findByCustomerIdWithStore(CUSTOMER_ID, pageable)).willReturn(page);
    given(favoriteMapper.toStoreResponse(favorite))
        .willReturn(FavoriteFixture.aStoreResponse(STORE_ID));

    // when
    PageResponse<FavoriteStoreResponse> response =
        favoriteService.getFavorites(CUSTOMER_ID, pageable);

    // then
    assertThat(response.totalCount()).isEqualTo(1L);
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).storeId()).isEqualTo(STORE_ID);
  }
}
