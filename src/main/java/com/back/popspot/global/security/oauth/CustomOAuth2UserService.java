package com.back.popspot.global.security.oauth;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.oauthAccount.entity.OauthAccount;
import com.back.popspot.domain.oauthAccount.entity.OauthProvider;
import com.back.popspot.domain.oauthAccount.repository.OauthAccountRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 구글에서 받은 사용자 정보로 우리 서비스의 User / OauthAccount 를 조회하거나 신규 가입시킨다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

	private final UserRepository userRepository;
	private final OauthAccountRepository oauthAccountRepository;

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) {
		OAuth2User oAuth2User = super.loadUser(userRequest);

		// registrationId 예: "google"
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		OauthProvider provider = OauthProvider.valueOf(registrationId.toUpperCase());

		Map<String, Object> attributes = oAuth2User.getAttributes();

		Object sub = attributes.get("sub");
		if (sub == null) {
			throw new BusinessException(ErrorCode.OAUTH2_LOGIN_FAILED);
		}

		String providerId = String.valueOf(sub);
		String email = (String) attributes.get("email");
		String name = (String) attributes.get("name");

		OauthAccount account = oauthAccountRepository
				.findByProviderAndProviderId(provider, providerId)
				.orElseGet(() -> register(provider, providerId, email, name));

		User user = account.getUser();
		return new CustomOAuth2User(user.getId(), user.getEmail(), user.getName(), attributes);
	}

	private OauthAccount register(OauthProvider provider, String providerId, String email, String name) {
		User user = userRepository.findByEmail(email)
				.orElseGet(() -> userRepository.save(User.create(email, name)));

		return oauthAccountRepository.save(OauthAccount.create(user, provider, providerId));
	}
}
