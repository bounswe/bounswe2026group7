package com.group7.backend.service;

import com.group7.backend.dto.request.NotificationPreferencesUpdateRequest;
import com.group7.backend.dto.response.UserNotificationPreferencesResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.entity.UserNotificationPreferences;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationPreferencesServiceTest {

    @Mock private UserNotificationPreferencesRepository repository;
    @Mock private UserRepository userRepository;

    private UserNotificationPreferencesService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new Mentee();
        user.setId(7L);
        service = new UserNotificationPreferencesService(repository, userRepository);
    }

    @Test
    void getLazilyCreatesRowWhenAbsent() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.save(any(UserNotificationPreferences.class)))
                .thenAnswer(i -> i.getArgument(0));

        UserNotificationPreferencesResponse response = service.get(7L);

        assertThat(response.isMatchesEnabled()).isTrue();
        assertThat(response.isMessagesEnabled()).isTrue();
        assertThat(response.isMeetingsEnabled()).isTrue();
        assertThat(response.isTasksEnabled()).isTrue();
        assertThat(response.isRequestsEnabled()).isTrue();

        ArgumentCaptor<UserNotificationPreferences> captor =
                ArgumentCaptor.forClass(UserNotificationPreferences.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void getReturnsExistingRow() {
        UserNotificationPreferences existing = new UserNotificationPreferences();
        existing.setUserId(7L);
        existing.setMatchesEnabled(false);
        existing.setMessagesEnabled(true);
        existing.setMeetingsEnabled(true);
        existing.setTasksEnabled(false);
        existing.setRequestsEnabled(true);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        UserNotificationPreferencesResponse response = service.get(7L);

        assertThat(response.isMatchesEnabled()).isFalse();
        assertThat(response.isTasksEnabled()).isFalse();
    }

    @Test
    void updateAppliesPartialPatch() {
        UserNotificationPreferences existing = new UserNotificationPreferences();
        existing.setUserId(7L);
        existing.setMatchesEnabled(true);
        existing.setMessagesEnabled(true);
        existing.setMeetingsEnabled(true);
        existing.setTasksEnabled(true);
        existing.setRequestsEnabled(true);

        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        NotificationPreferencesUpdateRequest request = new NotificationPreferencesUpdateRequest();
        request.setMatchesEnabled(false);
        request.setMessagesEnabled(null); // omitted: stays true

        UserNotificationPreferencesResponse response = service.update(7L, request);

        assertThat(response.isMatchesEnabled()).isFalse();
        assertThat(response.isMessagesEnabled()).isTrue();
        assertThat(response.isMeetingsEnabled()).isTrue();
    }
}
