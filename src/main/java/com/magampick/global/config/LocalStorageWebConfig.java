package com.magampick.global.config;

import com.magampick.global.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("!prod")
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
