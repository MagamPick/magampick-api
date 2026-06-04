package com.magampick.auth.oauth;

import com.magampick.auth.domain.OAuthProviderType;

public interface OAuthProvider {

  OAuthProviderType providerType();

  OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri);
}
