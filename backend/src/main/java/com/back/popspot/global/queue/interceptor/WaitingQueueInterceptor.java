package com.back.popspot.global.queue.interceptor;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.global.queue.exception.QueueCircuitOpenException;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WaitingQueueInterceptor implements HandlerInterceptor {

	private static final String RETRY_AFTER_SECONDS = "10";

	private final WaitingQueueRedisService queueService;
	private final ObjectMapper objectMapper;
	private final PopupStoreRepository popupStoreRepository;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
		throws Exception {
		Long popupId = extractPopupId(request.getRequestURI());
		if (popupId == null) {
			return true;
		}

		Long userId = resolveAuthenticatedUserId();
		if (userId == null) {
			// 비회원 → 큐 스킵, 상세 데이터 바로 반환
			return true;
		}

		String userIdStr = userId.toString();

		if (queueService.hasProceedPermission(popupId, userIdStr)) {
			return true;
		}

		if (queueService.isRecovering()) {
			writeServiceUnavailableResponse(response);
			return false;
		}

		PopupStore popup = popupStoreRepository.findById(popupId).orElse(null);
		if (popup == null || !popup.getReservationEndAt().isAfter(LocalDateTime.now())) {
			return true;
		}

		try {
			queueService.enqueue(popupId, userIdStr, popup.getReservationEndAt());
		} catch (QueueCircuitOpenException e) {
			writeServiceUnavailableResponse(response);
			return false;
		}

		writeWaitingResponse(response);
		return false;
	}

	/**
	 * SecurityContext에서 실제 로그인 사용자의 userId를 꺼낸다.
	 * AnonymousAuthenticationToken이거나 principal이 Long이 아니면 null 반환.
	 */
	private Long resolveAuthenticatedUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		if (auth.getPrincipal() instanceof Long userId) {
			return userId;
		}
		return null;
	}

	private Long extractPopupId(String uri) {
		String[] parts = uri.split("/");
		// /popups/{id} → ["", "popups", "{id}"]
		if (parts.length != 3 || !"popups".equals(parts[1])) {
			return null;
		}
		try {
			return Long.parseLong(parts[2]);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void writeWaitingResponse(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
		response.setContentType("application/json;charset=UTF-8");
		CommonApiResponse<Void> body = new CommonApiResponse<>("WAITING", "대기 중입니다.", null);
		response.getWriter().write(objectMapper.writeValueAsString(body));
		response.getWriter().flush();
	}

	private void writeServiceUnavailableResponse(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
		response.setContentType("application/json;charset=UTF-8");
		CommonApiResponse<Void> body = new CommonApiResponse<>(
			"QUEUE_TEMPORARILY_UNAVAILABLE",
			"대기열 서비스가 일시 중단됩니다. 잠시 후 다시 시도해주세요.",
			null
		);
		response.getWriter().write(objectMapper.writeValueAsString(body));
		response.getWriter().flush();
	}
}
