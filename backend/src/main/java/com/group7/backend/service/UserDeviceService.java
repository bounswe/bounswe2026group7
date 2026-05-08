package com.group7.backend.service;

import com.group7.backend.entity.User;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserDeviceRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Manages the per-user FCM token registry (#136). Registration is
 * idempotent on {@code token}; the per-user device cap is enforced here
 * (rather than in the schema) with oldest-first eviction by
 * {@code lastSeenAt}, so a re-registration of an existing token simply
 * refreshes its timestamp.
 */
@Service
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxPerUser;

    public UserDeviceService(UserDeviceRepository userDeviceRepository,
                             UserRepository userRepository,
                             Clock clock,
                             @Value("${app.devices.max-per-user:5}") int maxPerUser) {
        this.userDeviceRepository = userDeviceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxPerUser = maxPerUser;
    }

    /**
     * Idempotent on {@code token}. If the token already exists for the
     * caller, refresh {@code lastSeenAt}. If it exists for a different
     * user (rare; e.g. shared phone), reassign it. If the caller is at
     * the cap, evict the oldest-by-{@code lastSeenAt} device first.
     */
    @Transactional
    public UserDevice register(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<UserDevice> existing = userDeviceRepository.findByToken(token);
        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.setUser(user);
            device.setLastSeenAt(now);
            return userDeviceRepository.save(device);
        }

        if (userDeviceRepository.countByUser_Id(userId) >= maxPerUser) {
            userDeviceRepository.findFirstByUser_IdOrderByLastSeenAtAsc(userId)
                    .ifPresent(userDeviceRepository::delete);
        }

        UserDevice device = new UserDevice();
        device.setUser(user);
        device.setToken(token);
        device.setLastSeenAt(now);
        return userDeviceRepository.save(device);
    }

    /**
     * Idempotent. Silently no-op if the token doesn't exist or belongs
     * to a different user — the request still authenticates the caller,
     * so refusing to delete tokens not owned by the caller is enforced
     * here (avoids cross-user token leakage as a side channel).
     */
    @Transactional
    public void unregister(Long userId, String token) {
        userDeviceRepository.findByToken(token).ifPresent(device -> {
            if (device.getUser().getId().equals(userId)) {
                userDeviceRepository.delete(device);
            }
        });
    }
}
