package com.magampick.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCI Object Storage 설정. {@code namespace}/{@code region}/{@code bucket} 은 모든 프로파일 공통(값만 다름),
 * {@code configPath}/{@code configProfile} 은 local 프로파일의 API Key 인증에만 쓰인다 (dev/prod 는 Instance
 * Principal 인증이라 무시).
 */
@ConfigurationProperties("magampick.storage.oci")
public record OciStorageProperties(
    String namespace, String region, String bucket, String configPath, String configProfile) {}
