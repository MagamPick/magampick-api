package com.magampick.global.storage;

import com.magampick.global.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

  private final StorageProperties storageProperties;

  @Override
  public String upload(MultipartFile file) {
    String ext = extractExtension(file.getOriginalFilename());
    LocalDate now = LocalDate.now();
    String relativePath =
        now.getYear() + "/" + now.getMonthValue() + "/" + UUID.randomUUID() + "." + ext;
    Path target = Path.of(storageProperties.rootPath()).resolve(relativePath);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(file.getInputStream(), target);
    } catch (IOException e) {
      throw new BusinessException(StorageErrorCode.IMAGE_UPLOAD_FAILED, e);
    }
    log.info("file uploaded. path={}", target);
    return "/uploads/" + relativePath;
  }

  private String extractExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "bin";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }
}
