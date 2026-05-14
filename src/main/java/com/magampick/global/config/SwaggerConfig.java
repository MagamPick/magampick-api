package com.magampick.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc-openapi 설정. 역할별 prefix 와 일관되게 3개 그룹으로 분리한다 (api-convention §12). */
@Configuration
public class SwaggerConfig {

  private static final String SECURITY_SCHEME_NAME = "BearerAuth";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("Magampick API").version("v1").description("마감 임박 베이커리/카페 픽업 플랫폼"))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }

  @Bean
  public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
        .group("1. Public (소비자)")
        .pathsToMatch("/api/v1/**")
        .pathsToExclude("/api/v1/seller/**", "/api/v1/admin/**")
        .build();
  }

  @Bean
  public GroupedOpenApi sellerApi() {
    return GroupedOpenApi.builder()
        .group("2. Seller (사장)")
        .pathsToMatch("/api/v1/seller/**")
        .build();
  }

  @Bean
  public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
        .group("3. Admin (관리자)")
        .pathsToMatch("/api/v1/admin/**")
        .build();
  }
}
