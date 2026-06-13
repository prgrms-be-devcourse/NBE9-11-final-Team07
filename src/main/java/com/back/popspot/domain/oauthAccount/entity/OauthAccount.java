package com.back.popspot.domain.oauthAccount.entity;

import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "oauth_account")
public class OauthAccount extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OauthProvider provider;

	@Column(name = "provider_id", length = 100, nullable = false)
	private String providerId;

	private OauthAccount(User user, OauthProvider provider, String providerId) {
		this.user = user;
		this.provider = provider;
		this.providerId = providerId;
	}

	public static OauthAccount create(User user, OauthProvider provider, String providerId) {
		return new OauthAccount(user, provider, providerId);
	}
}
