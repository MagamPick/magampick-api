package com.magampick.auth.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/test-support")
public class SellerTestSupportController {

  public record Ping(String value) {}

  @GetMapping("/ping")
  public Ping ping() {
    return new Ping("ok");
  }
}
