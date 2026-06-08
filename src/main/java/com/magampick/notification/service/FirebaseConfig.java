package com.magampick.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Admin SDK(FCM) 빈 구성. {@code app.fcm.mock-enabled=false}(기본값) 일 때만 로딩 — mock 모드에서는 {@link
 * FirebaseProperties} 검증을 건너뛰므로 자격증명 없이 기동 가능(SOLAPI {@code SolapiConfig} 패턴과 동일). 자격증명은 서비스 계정
 * JSON 을 base64 로 인코딩한 환경변수(FCM_CREDENTIALS) — 한글 경로 회피 + dev/prod 시크릿으로 그대로 연결.
 */
@Configuration
@ConditionalOnProperty(name = "app.fcm.mock-enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseProperties properties) throws IOException {
    byte[] serviceAccountJson = Base64.getDecoder().decode(properties.credentials());
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson));
    FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
    FirebaseApp app =
        FirebaseApp.getApps().isEmpty()
            ? FirebaseApp.initializeApp(options)
            : FirebaseApp.getInstance();
    return FirebaseMessaging.getInstance(app);
  }
}
