package com.group7.backend.event;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ReportFanoutListener} (#135). Mocks the
 * repositories and the notification publisher; pins three behaviours:
 * <ul>
 *   <li>One notification per admin id returned by {@code findAllAdminIds}.</li>
 *   <li>Reporter-name fallback to {@code "Someone"} when the reporter has
 *       been deleted between submit and listener execution.</li>
 *   <li>No fan-out when the admin list is empty.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReportFanoutListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @InjectMocks private ReportFanoutListener listener;

    @Test
    void onReportSubmitted_publishesOneNotificationPerAdmin() {
        User reporter = mentee(7L, "Ada");
        when(userRepository.findById(7L)).thenReturn(Optional.of(reporter));
        when(userRepository.findAllAdminIds()).thenReturn(List.of(100L, 101L, 102L));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, ReportTargetType.USER));

        verify(notificationEventPublisher)
                .publishReportReceived(eq(100L), eq("Ada"), eq(ReportTargetType.USER));
        verify(notificationEventPublisher)
                .publishReportReceived(eq(101L), eq("Ada"), eq(ReportTargetType.USER));
        verify(notificationEventPublisher)
                .publishReportReceived(eq(102L), eq("Ada"), eq(ReportTargetType.USER));
    }

    @Test
    void onReportSubmitted_fallsBackToGenericName_whenReporterDeleted() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        when(userRepository.findAllAdminIds()).thenReturn(List.of(100L));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, ReportTargetType.MENTORSHIP));

        verify(notificationEventPublisher)
                .publishReportReceived(eq(100L), eq("Someone"), eq(ReportTargetType.MENTORSHIP));
    }

    @Test
    void onReportSubmitted_emitsNoNotifications_whenNoAdminsExist() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee(7L, "Ada")));
        when(userRepository.findAllAdminIds()).thenReturn(List.of());

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, ReportTargetType.POST));

        verify(notificationEventPublisher, never())
                .publishReportReceived(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onReportSubmitted_swallowsRuntimeFailures_doesNotPropagate() {
        // Listener's top-level catch logs and returns, so async executor
        // never sees an uncaught exception from a transient DB blip.
        when(userRepository.findById(7L)).thenThrow(new RuntimeException("DB down"));

        listener.onReportSubmitted(new ReportSubmittedEvent(
                500L, 7L, ReportTargetType.USER));

        verify(notificationEventPublisher, never())
                .publishReportReceived(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private static Mentee mentee(Long id, String firstName) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(firstName + "@test.com");
        return m;
    }
}
