package com.magampick.favorite.fixture;

import com.magampick.customer.domain.Customer;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
import com.magampick.store.domain.Store;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class FavoriteFixture {

  private FavoriteFixture() {}

  public static Favorite aFavorite(Customer customer, Store store) {
    return Favorite.builder().customer(customer).store(store).build();
  }

  public static FavoriteAddResponse aAddResponse(Long storeId) {
    return new FavoriteAddResponse(storeId, OffsetDateTime.now(ZoneOffset.ofHours(9)));
  }

  public static FavoriteStoreResponse aStoreResponse(Long storeId) {
    return new FavoriteStoreResponse(
        storeId,
        "동네빵집",
        "서울 강남구 테헤란로 427",
        "/uploads/store.jpg",
        OffsetDateTime.now(ZoneOffset.ofHours(9)));
  }
}
