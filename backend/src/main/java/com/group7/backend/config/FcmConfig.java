package com.group7.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.group7.backend.repository.UserDeviceRepository;
import com.group7.backend.service.FcmPushDeliveryService;
import com.group7.backend.service.NoOpPushDeliveryService;
import com.group7.backend.service.PushDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Conditional FCM wiring (#136).
 *
 * <p>If {@code app.fcm.enabled} is {@code true} AND
 * {@code app.fcm.credentials-path} resolves to a readable service-account
 * JSON file, initialise {@link FirebaseApp} and expose
 * {@link FcmPushDeliveryService} as the active {@link PushDeliveryService}.
 * Otherwise (default for dev / CI), return {@link NoOpPushDeliveryService}.
 *
 * <p>The credentials file is resolved on the local filesystem only — no
 * classpath fallback. Operators set {@code FCM_CREDENTIALS_PATH} to an
 * absolute path of the JSON pulled from the Firebase console (Service
 * accounts → Generate new private key).
 */
@Configuration
public class FcmConfig {

    private static final Logger log = LoggerFactory.getLogger(FcmConfig.class);

    private final boolean enabled;
    private final String credentialsPath;

    public FcmConfig(@Value("${app.fcm.enabled:true}") boolean enabled,
                     @Value("${app.fcm.credentials-path:}") String credentialsPath) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
    }

    @Bean
    public PushDeliveryService pushDeliveryService(UserDeviceRepository userDeviceRepository) {
        FirebaseMessaging messaging = tryInitialiseFirebase();
        if (messaging == null) {
            log.info("FCM credentials not configured; using NoOpPushDeliveryService.");
            return new NoOpPushDeliveryService();
        }
        log.info("FCM initialised; using FcmPushDeliveryService.");
        return new FcmPushDeliveryService(messaging, userDeviceRepository);
    }

    private FirebaseMessaging tryInitialiseFirebase() {
        if (!enabled) {
            return null;
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return null;
        }
        Path path = Path.of(credentialsPath);
        if (!Files.isReadable(path)) {
            log.warn("FCM credentials path '{}' is not readable; falling back to no-op.",
                    credentialsPath);
            return null;
        }
        try (FileInputStream stream = new FileInputStream(path.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (IOException ex) {
            log.warn("Failed to initialise Firebase from '{}': {}",
                    credentialsPath, ex.getMessage());
            return null;
        }
    }
}
