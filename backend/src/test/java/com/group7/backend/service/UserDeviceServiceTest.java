package com.group7.backend.service;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserDeviceRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeviceServiceTest {

    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private UserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneOffset.UTC);
    private UserDeviceService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new Mentee();
        user.setId(7L);
        service = new UserDeviceService(userDeviceRepository, userRepository, clock, 5);
    }

    @Test
    void registerInsertsNewTokenWhenAbsent() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByToken("tok-A")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUser_Id(7L)).thenReturn(0L);
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(i -> i.getArgument(0));

        UserDevice saved = service.register(7L, "tok-A");

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isEqualTo("tok-A");
        assertThat(saved.getLastSeenAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(userDeviceRepository, never()).delete(any());
    }

    @Test
    void registerExistingTokenRefreshesLastSeenAt() {
        UserDevice existing = new UserDevice();
        existing.setUser(user);
        existing.setToken("tok-A");
        existing.setLastSeenAt(OffsetDateTime.now(clock).minusDays(2));

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByToken("tok-A")).thenReturn(Optional.of(existing));
        when(userDeviceRepository.save(existing)).thenReturn(existing);

        UserDevice result = service.register(7L, "tok-A");

        assertThat(result.getLastSeenAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(userDeviceRepository, never()).delete(any());
    }

    @Test
    void registerEvictsOldestWhenAtCap() {
        UserDevice oldest = new UserDevice();
        oldest.setId(99L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByToken("tok-NEW")).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUser_Id(7L)).thenReturn(5L);
        when(userDeviceRepository.findFirstByUser_IdOrderByLastSeenAtAsc(7L))
                .thenReturn(Optional.of(oldest));
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(i -> i.getArgument(0));

        service.register(7L, "tok-NEW");

        verify(userDeviceRepository).delete(oldest);
        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("tok-NEW");
    }

    @Test
    void registerThrowsWhenUserMissing() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(7L, "tok"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userDeviceRepository, never()).save(any());
    }

    @Test
    void unregisterDeletesOwnedToken() {
        UserDevice device = new UserDevice();
        device.setUser(user);
        device.setToken("tok-A");
        when(userDeviceRepository.findByToken("tok-A")).thenReturn(Optional.of(device));

        service.unregister(7L, "tok-A");

        verify(userDeviceRepository).delete(device);
    }

    @Test
    void unregisterIgnoresTokenOwnedByDifferentUser() {
        User otherUser = new Mentee();
        otherUser.setId(99L);
        UserDevice device = new UserDevice();
        device.setUser(otherUser);
        device.setToken("tok-A");
        when(userDeviceRepository.findByToken("tok-A")).thenReturn(Optional.of(device));

        service.unregister(7L, "tok-A");

        verify(userDeviceRepository, never()).delete(any());
    }

    @Test
    void unregisterIsNoOpWhenTokenMissing() {
        when(userDeviceRepository.findByToken("tok-X")).thenReturn(Optional.empty());

        service.unregister(7L, "tok-X");

        verify(userDeviceRepository, never()).delete(any());
    }
}
