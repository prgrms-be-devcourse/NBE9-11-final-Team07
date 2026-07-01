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
import com.back.popspot.global.queue.interceptor.WaitingQueueInterceptor;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("종료 팝업 enqueue 스킵 단위 테스트")
class WaitingQueueInterceptorClosedPopupTest {

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
    @DisplayName("종료 팝업(reservationEndAt < now) → return true, enqueue 미호출")
    void closedPopup_returnTrue_enqueueSkipped() throws Exception {
        PopupStore popup = mock(PopupStore.class);
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(false);
        given(popupStoreRepository.findById(1L)).willReturn(Optional.of(popup));
        given(popup.getReservationEndAt()).willReturn(LocalDateTime.now().minusSeconds(1));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(queueService, never()).enqueue(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("진행중 팝업(reservationEndAt > now, proceed 권한 없음) → enqueue 호출, 202 대기")
    void openPopup_enqueueCalledAndReturn202() throws Exception {
        PopupStore popup = mock(PopupStore.class);
        LocalDateTime futureEndAt = LocalDateTime.now().plusDays(1);
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(false);
        given(popupStoreRepository.findById(1L)).willReturn(Optional.of(popup));
        given(popup.getReservationEndAt()).willReturn(futureEndAt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
        verify(queueService).enqueue(1L, "1", futureEndAt);
    }

    @Test
    @DisplayName("경계: reservationEndAt == now → 종료로 처리(return true, enqueue 미호출)")
    void exactNow_treatedAsClosed() throws Exception {
        PopupStore popup = mock(PopupStore.class);
        // preHandle 진입 시 실제 now >= 여기서 캡처한 exactNow → isAfter 항상 false → 종료
        LocalDateTime exactNow = LocalDateTime.now();
        given(queueService.hasProceedPermission(anyLong(), anyString())).willReturn(false);
        given(queueService.isRecovering()).willReturn(false);
        given(popupStoreRepository.findById(1L)).willReturn(Optional.of(popup));
        given(popup.getReservationEndAt()).willReturn(exactNow);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/popups/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(queueService, never()).enqueue(anyLong(), anyString(), any());
    }
}
