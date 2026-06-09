package com.magampick.announcement.controller;

import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 공지사항 목록 조회 API. 인증 필요 (any role). */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcement (Public)", description = "공지사항 목록 조회 API — 인증 필요")
public class AnnouncementController {

  private final AnnouncementService announcementService;

  @GetMapping
  @Operation(summary = "공지사항 목록 조회", description = "핀 우선 → 발행일 최신 → id 내림차순 정렬. 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public List<AnnouncementResponse> list() {
    return announcementService.list();
  }
}
