package com.group7.backend.event;

import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ReportFanoutListener} (#135). Mocks the
 * repository and the notification publisher; pins four behaviours:
 * <ul>
 *   <li>One notification per admin id returned by {@code findAllAdminIds},
 *       using {@code reporterFirstName} carried by the event (no extra
 *       per-fanout user lookup).</li>
 *   <li>Fallback to {@code "Someone"} when the publisher couldn't load
 *       the reporter and the event field is null.</li>
 *   <li>No fan-out when the admin list is empty.</li>
 *   <li>Top-level catch swallows runtime failures so the async executor
 *       never sees an uncaught exception.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReportFanoutListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @InjectMocks private ReportFanoutListener listener;

    @Test
    void onReportSubmitted_publishesOneNotificationPerAdmin() {
        when(userRepository.findAllAdminIds()).thenReturn(List.of(100L, 101L, 102L));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, "Ada", ReportTargetType.USER));

        verify(notificationEventPublisher)
                .publishReportReceived(eq(100L), eq("Ada"), eq(ReportTargetType.USER));
        verify(notificationEventPublisher)
                .publishReportReceived(eq(101L), eq("Ada"), eq(ReportTargetType.USER));
        verify(notificationEventPublisher)
                .publishReportReceived(eq(102L), eq("Ada"), eq(ReportTargetType.USER));
        // Listener trusts the event and does NOT do its own user lookup.
        verify(userRepository, never()).findById(any());
    }

    @Test
    void onReportSubmitted_fallsBackToGenericName_whenEventCarriesNullReporterName() {
        when(userRepository.findAllAdminIds()).thenReturn(List.of(100L));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, null, ReportTargetType.MENTORSHIP));

        verify(notificationEventPublisher)
                .publishReportReceived(eq(100L), eq("Someone"), eq(ReportTargetType.MENTORSHIP));
    }

    @Test
    void onReportSubmitted_emitsNoNotifications_whenNoAdminsExist() {
        when(userRepository.findAllAdminIds()).thenReturn(List.of());

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, "Ada", ReportTargetType.POST));

        verify(notificationEventPublisher, never())
                .publishReportReceived(any(), any(), any());
    }

    @Test
    void onReportSubmitted_swallowsRuntimeFailures_doesNotPropagate() {
        // Listener's top-level catch logs and returns, so async executor
        // never sees an uncaught exception from a transient DB blip.
        when(userRepository.findAllAdminIds()).thenThrow(new RuntimeException("DB down"));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, "Ada", ReportTargetType.USER));

        verify(notificationEventPublisher, never())
                .publishReportReceived(any(), any(), any());
    }
}
