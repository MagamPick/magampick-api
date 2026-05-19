package com.magampick.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("magampick.stores")
public record StoreProperties(boolean autoApprove) {}
