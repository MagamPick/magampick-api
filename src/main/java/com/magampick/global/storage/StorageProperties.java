package com.magampick.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("magampick.storage.local")
public record StorageProperties(String rootPath) {}
