package com.magampick.global.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {}
