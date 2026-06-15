package com.magampick.announcement.mapper;

import com.magampick.announcement.domain.Announcement;
import com.magampick.announcement.dto.AnnouncementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 공지사항 도메인 MapStruct 매퍼. */
@Mapper(componentModel = "spring")
public interface AnnouncementMapper {

  /**
   * Announcement 엔티티 → AnnouncementResponse.
   *
   * <p>publishedAt → date 필드명 매핑.
   */
  @Mapping(target = "date", source = "publishedAt")
  AnnouncementResponse toResponse(Announcement announcement);
}
