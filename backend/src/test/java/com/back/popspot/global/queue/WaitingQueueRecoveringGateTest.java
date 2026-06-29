package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.global.queue.exception.QueueCircuitOpenException;
import com.back.popspot.global.queue.interceptor.WaitingQueueInterceptor;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * recovering 게이트 단위 테스트.
 *
 * <p>트리거(CB 전이 리스너, QueueRecoveryService)는 다음 Phase에서 연결.
 * 이번 Phase는 recovering 필드 + 게이트 조건만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("recovering 게이트 단위 테스트")
class WaitingQueueRecoveringGateTest {

    @Mock
    WaitingQueueRedisService queueService;

    @Mock
    PopupStoreRepository popupStoreRepository;

    WaitingQueueInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WaitingQueueInterceptor(queueService, new ObjectMapper(), popupStoreRepository);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("recovering=true, CB=CLOSED → 503 (recovering 게이트가 차단)")
    void recovering_true_cbClosed_차단() throws Exception {
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
    }

    @Test
    @DisplayName("recovering=false, CB=CLOSED → 202 대기 응답 (정상 enqueue)")
    void recovering_false_cbClosed_정상통과() throws Exception {
        PopupStore popup = mock(PopupStore.class);
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(false);
        given(popupStoreRepository.findById(1L)).willReturn(Optional.of(popup));
        given(popup.getReservationEndAt()).willReturn(LocalDateTime.of(2099, 12, 31, 23, 59));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
    }

    @Test
    @DisplayName("recovering=true, CB=OPEN → 503 (recovering 게이트가 먼저 차단, enqueue 미호출)")
    void recovering_true_cbOpen_차단() throws Exception {
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(true);
        // CB=OPEN 상태라면 enqueue() 호출 시 QueueCircuitOpenException이 발생하겠지만,
        // recovering 게이트가 먼저 막으므로 enqueue() 자체에 도달하지 않는다.

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(queueService, never()).enqueue(anyLong(), anyString(), any());
    }
}
