package com.magampick.global.config;

import com.magampick.global.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 테스트(test 프로파일) 전용 {@code /uploads/**} 정적 리소스 핸들러. 런타임은 OCI public URL 직접 접근이라 불필요. */
@Configuration
@Profile("test")
@RequiredArgsConstructor
@EnableConfigurationProperties(StorageProperties.class)
public class LocalStorageWebConfig implements WebMvcConfigurer {

  private final StorageProperties storageProperties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations("file:" + storageProperties.rootPath() + "/");
  }
}
