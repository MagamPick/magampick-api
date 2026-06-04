package com.magampick.global.storage;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OCI Object Storage 클라이언트 구성. test 프로파일에서는 비활성 — 테스트가 실제 OCI 에 붙지 않게 한다. 인증은 프로파일별로 분리한다: local 은
 * {@code ~/.oci/config} 의 API Key, dev/prod 는 OCI 컴퓨트 인스턴스의 Instance Principal.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(OciStorageProperties.class)
public class OciStorageConfig {

  /** local: 개발자 머신의 {@code ~/.oci/config} API Key 프로파일로 인증. */
  @Bean
  @Profile("local")
  BasicAuthenticationDetailsProvider configFileAuthProvider(OciStorageProperties properties)
      throws IOException {
    return new ConfigFileAuthenticationDetailsProvider(
        properties.configPath(), properties.configProfile());
  }

  /** dev/prod: OCI 컴퓨트 인스턴스의 Instance Principal 로 인증 (키 파일 불필요). */
  @Bean
  @Profile("!local")
  BasicAuthenticationDetailsProvider instancePrincipalAuthProvider() {
    return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
  }

  @Bean
  ObjectStorage objectStorageClient(
      BasicAuthenticationDetailsProvider authProvider, OciStorageProperties properties) {
    return ObjectStorageClient.builder()
        .region(Region.fromRegionId(properties.region()))
        .build(authProvider);
  }
}
