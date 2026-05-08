package com.group7.backend.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.repository.UserDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushDeliveryServiceTest {

    @Mock private FirebaseMessaging firebaseMessaging;
    @Mock private UserDeviceRepository userDeviceRepository;

    private FcmPushDeliveryService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new FcmPushDeliveryService(firebaseMessaging, userDeviceRepository);
        user = new Mentee();
        user.setId(7L);
    }

    @Test
    void skipsDispatchWhenNoDevices() throws Exception {
        when(userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(7L)).thenReturn(List.of());

        service.send(7L, NotificationType.NEW_MESSAGE, "title", "body");

        verify(firebaseMessaging, never()).sendEachForMulticast(any());
    }

    @Test
    void sendsToAllRegisteredDevices() throws Exception {
        when(userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(7L))
                .thenReturn(List.of(device("tok-A"), device("tok-B")));

        BatchResponse response = mockBatchResponse(
                mockSendResponseSuccess(),
                mockSendResponseSuccess());
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

        service.send(7L, NotificationType.NEW_MESSAGE, "title", "body");

        verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
        verify(userDeviceRepository, never()).deleteByToken(any());
    }

    @Test
    void prunesUnregisteredTokens() throws Exception {
        when(userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(7L))
                .thenReturn(List.of(device("tok-A"), device("tok-B")));

        BatchResponse response = mockBatchResponse(
                mockSendResponseSuccess(),
                mockSendResponseFailure(MessagingErrorCode.UNREGISTERED));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

        service.send(7L, NotificationType.NEW_MESSAGE, "title", "body");

        verify(userDeviceRepository).deleteByToken("tok-B");
        verify(userDeviceRepository, never()).deleteByToken("tok-A");
    }

    @Test
    void swallowsSdkExceptions() throws Exception {
        when(userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(7L))
                .thenReturn(List.of(device("tok-A")));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(mock(FirebaseMessagingException.class));

        // Asserts no throw — best-effort contract.
        service.send(7L, NotificationType.NEW_MESSAGE, "title", "body");
    }

    private UserDevice device(String token) {
        UserDevice d = new UserDevice();
        d.setUser(user);
        d.setToken(token);
        return d;
    }

    private static BatchResponse mockBatchResponse(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(responses));
        return batch;
    }

    private static SendResponse mockSendResponseSuccess() {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(true);
        return r;
    }

    private static SendResponse mockSendResponseFailure(MessagingErrorCode code) {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(code);
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(false);
        when(r.getException()).thenReturn(ex);
        return r;
    }
}
