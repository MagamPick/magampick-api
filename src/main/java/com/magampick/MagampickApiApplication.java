package com.magampick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MagampickApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(MagampickApiApplication.class, args);
  }
}
