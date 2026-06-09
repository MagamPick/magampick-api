package com.magampick.announcement.controller;

import com.magampick.announcement.dto.AdminAnnouncementCreateRequest;
import com.magampick.announcement.dto.AdminAnnouncementUpdateRequest;
import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 공지사항 CRUD API. ROLE_ADMIN 전용 (/api/v1/admin/** SecurityConfig 에서 보호됨). */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcement (Admin)", description = "관리자 공지사항 관리 API — ROLE_ADMIN 전용")
public class AdminAnnouncementController {

  private final AnnouncementService announcementService;

  @PostMapping
  @Operation(summary = "공지사항 생성", description = "관리자 전용. publishedAt = 생성 당일.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<AnnouncementResponse> create(
      @Valid @RequestBody AdminAnnouncementCreateRequest request) {
    AnnouncementResponse response = announcementService.create(request);
    URI location = URI.create("/api/v1/admin/announcements/" + response.id());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @Operation(summary = "공지사항 전체 목록 조회 (관리자)", description = "관리자 전용 목록 — 정렬 동일.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public List<AnnouncementResponse> listAll() {
    return announcementService.list();
  }

  @PatchMapping("/{announcementId}")
  @Operation(summary = "공지사항 수정 (부분)", description = "관리자 전용. null 필드는 미수정.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
  })
  public AnnouncementResponse update(
      @PathVariable Long announcementId,
      @Valid @RequestBody AdminAnnouncementUpdateRequest request) {
    return announcementService.update(announcementId, request);
  }

  @DeleteMapping("/{announcementId}")
  @Operation(summary = "공지사항 삭제", description = "관리자 전용.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
  })
  public ResponseEntity<Void> delete(@PathVariable Long announcementId) {
    announcementService.delete(announcementId);
    return ResponseEntity.noContent().build();
  }
}
