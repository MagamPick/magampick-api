package com.magampick.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

  String upload(MultipartFile file);

  /**
   * 저장소에 보관된 파일 삭제. **best effort** — 호출 측이 try/catch 없이 부르더라도 비즈니스 흐름에 영향 주면 안 된다 (실패 시 내부 log
   * warn). 알 수 없는 URL 형태는 무시한다.
   */
  void delete(String url);
}
