package com.group7.backend.scheduler;

import com.group7.backend.service.MentorshipAutoCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipAutoCompletionSchedulerTest {

    @Mock private MentorshipAutoCompletionService service;

    private MentorshipAutoCompletionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MentorshipAutoCompletionScheduler(service);
    }

    @Test
    void sweepDelegatesToService() {
        when(service.autoCompleteExpired()).thenReturn(0);

        scheduler.sweep();

        verify(service).autoCompleteExpired();
    }

    @Test
    void sweepLogsWhenRowsProcessed() {
        when(service.autoCompleteExpired()).thenReturn(3);

        scheduler.sweep();

        verify(service).autoCompleteExpired();
    }
}
