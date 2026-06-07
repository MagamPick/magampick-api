package com.magampick.favorite.fixture;

import com.magampick.customer.domain.Customer;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteListResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
import com.magampick.store.domain.Store;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class FavoriteFixture {

  private FavoriteFixture() {}

  public static Favorite aFavorite(Customer customer, Store store) {
    return Favorite.builder().customer(customer).store(store).build();
  }

  public static FavoriteAddResponse aAddResponse(Long storeId) {
    return new FavoriteAddResponse(storeId, OffsetDateTime.now(ZoneOffset.ofHours(9)));
  }

  public static FavoriteStoreResponse aStoreResponse(Long storeId) {
    return new FavoriteStoreResponse(storeId, "동네빵집", "/uploads/store.jpg", 1.5, 4.3, 2L);
  }

  public static FavoriteListResponse aListResponse(Long storeId) {
    return new FavoriteListResponse(List.of(aStoreResponse(storeId)), 1L, 2L);
  }
}
