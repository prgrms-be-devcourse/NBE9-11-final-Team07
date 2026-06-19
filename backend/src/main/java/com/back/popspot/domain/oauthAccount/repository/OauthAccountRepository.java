package com.back.popspot.domain.oauthAccount.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.oauthAccount.entity.OauthAccount;
import com.back.popspot.domain.oauthAccount.entity.OauthProvider;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {
	Optional<OauthAccount> findByProviderAndProviderId(OauthProvider provider, String providerId);
}
