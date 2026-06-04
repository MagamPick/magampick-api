package com.magampick.global.storage;

import com.magampick.global.exception.BusinessException;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCI Object Storage 구현. local·dev·prod 런타임에서 사용되며, test 프로파일에서는 {@link LocalStorageService} 가 대신
 * 동작한다. object 이름은 {@code yyyy/M/{uuid}.{ext}} 규칙으로 {@link LocalStorageService} 와 동일하게 부여하고, public
 * read 버킷의 직접 접근 URL 을 반환한다.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class OciStorageService implements StorageService {

  private final ObjectStorage objectStorage;
  private final OciStorageProperties properties;

  @Override
  public String upload(MultipartFile file) {
    String ext = extractExtension(file.getOriginalFilename());
    LocalDate now = LocalDate.now();
    String objectName =
        now.getYear() + "/" + now.getMonthValue() + "/" + UUID.randomUUID() + "." + ext;
    try {
      objectStorage.putObject(
          PutObjectRequest.builder()
              .namespaceName(properties.namespace())
              .bucketName(properties.bucket())
              .objectName(objectName)
              .contentLength(file.getSize())
              .contentType(file.getContentType())
              .putObjectBody(file.getInputStream())
              .build());
    } catch (IOException | BmcException e) {
      throw new BusinessException(StorageErrorCode.IMAGE_UPLOAD_FAILED, e);
    }
    String url = buildPublicUrl(objectName);
    log.info("파일 업로드 완료. url={}", url);
    return url;
  }

  @Override
  public void delete(String url) {
    String objectName = extractObjectName(url);
    if (objectName == null) {
      return; // 알 수 없는 URL 형태 — best effort, 무시
    }
    try {
      objectStorage.deleteObject(
          DeleteObjectRequest.builder()
              .namespaceName(properties.namespace())
              .bucketName(properties.bucket())
              .objectName(objectName)
              .build());
      log.info("파일 삭제 완료. objectName={}", objectName);
    } catch (BmcException e) {
      log.warn("파일 삭제 실패 (무시). objectName={}", objectName, e);
    }
  }

  /** {@code https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/{bucket}/o/{object}} */
  private String buildPublicUrl(String objectName) {
    return "https://objectstorage."
        + properties.region()
        + ".oraclecloud.com/n/"
        + properties.namespace()
        + "/b/"
        + properties.bucket()
        + "/o/"
        + objectName;
  }

  /** public URL 의 {@code /o/} 뒤가 object 이름. 형식이 다르면 null — 알 수 없는 URL 은 무시 (best effort 삭제). */
  private String extractObjectName(String url) {
    if (url == null) {
      return null;
    }
    int idx = url.indexOf("/o/");
    if (idx < 0) {
      return null;
    }
    return url.substring(idx + 3);
  }

  private String extractExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "bin";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }
}
