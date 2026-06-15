package com.magampick.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class OciStorageServiceTest {

  @Mock ObjectStorage objectStorage;

  private final OciStorageProperties properties =
      new OciStorageProperties(
          "axuqml8mwqye", "ap-chuncheon-1", "magampick-dev-uploads", "/x/.oci/config", "APIKEY");

  private OciStorageService service;

  @BeforeEach
  void setUp() {
    service = new OciStorageService(objectStorage, properties);
  }

  @Test
  void 업로드_성공시_putObject_호출하고_public_URL_반환() {
    MockMultipartFile file =
        new MockMultipartFile("image", "photo.JPG", "image/jpeg", "bytes".getBytes());

    String url = service.upload(file);

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    then(objectStorage).should().putObject(captor.capture());
    PutObjectRequest request = captor.getValue();
    assertThat(request.getNamespaceName()).isEqualTo("axuqml8mwqye");
    assertThat(request.getBucketName()).isEqualTo("magampick-dev-uploads");
    assertThat(request.getContentLength()).isEqualTo(5L);
    // object 이름은 yyyy/M/{uuid}.{ext} — 확장자는 소문자로 정규화 (.JPG → .jpg)
    assertThat(request.getObjectName()).matches("\\d{4}/\\d{1,2}/[0-9a-f-]+\\.jpg");
    assertThat(url)
        .isEqualTo(
            "https://objectstorage.ap-chuncheon-1.oraclecloud.com"
                + "/n/axuqml8mwqye/b/magampick-dev-uploads/o/"
                + request.getObjectName());
  }

  @Test
  void 삭제시_URL의_object이름을_파싱해_deleteObject_호출() {
    String url =
        "https://objectstorage.ap-chuncheon-1.oraclecloud.com"
            + "/n/axuqml8mwqye/b/magampick-dev-uploads/o/2026/6/abc.jpg";

    service.delete(url);

    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    then(objectStorage).should().deleteObject(captor.capture());
    assertThat(captor.getValue().getObjectName()).isEqualTo("2026/6/abc.jpg");
  }

  @Test
  void object이름을_찾을수_없는_URL_삭제는_무시() {
    service.delete("/uploads/2026/6/old.jpg"); // 예전 로컬 형식 — /o/ 없음

    then(objectStorage).should(never()).deleteObject(any());
  }
}
