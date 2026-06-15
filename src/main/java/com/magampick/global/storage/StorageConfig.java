package com.magampick.global.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 테스트(test 프로파일) 전용 로컬 저장소 설정. 런타임 OCI 구성은 {@link OciStorageConfig}. */
@Configuration
@Profile("test")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {}
