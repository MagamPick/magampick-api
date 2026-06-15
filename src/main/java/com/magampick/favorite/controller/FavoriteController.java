package com.magampick.favorite.controller;

import com.magampick.favorite.dto.FavoriteAddRequest;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteListResponse;
import com.magampick.favorite.service.FavoriteService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorite (Customer)", description = "소비자 즐겨찾기 관리 API")
public class FavoriteController {

  private final FavoriteService favoriteService;

  @PostMapping
  @Operation(summary = "즐겨찾기 등록")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공 (이미 등록된 경우도 201)"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "매장 없음"),
    @ApiResponse(responseCode = "409", description = "단골 한도 초과 (FAVORITE_LIMIT_REACHED)")
  })
  public ResponseEntity<FavoriteAddResponse> add(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody @Valid FavoriteAddRequest request) {
    FavoriteAddResponse response =
        favoriteService.addFavorite(userDetails.getUserId(), request.storeId());
    return ResponseEntity.created(
            URI.create("/api/v1/customers/me/favorites/" + response.storeId()))
        .body(response);
  }

  @DeleteMapping("/{storeId}")
  @Operation(summary = "즐겨찾기 해제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "해제 성공 (미등록 상태여도 204)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    favoriteService.removeFavorite(userDetails.getUserId(), storeId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  @Operation(summary = "단골 매장 목록 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public FavoriteListResponse list(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return favoriteService.getFavorites(userDetails.getUserId());
  }
}
