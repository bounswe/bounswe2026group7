package com.group7.backend.repository;

import com.group7.backend.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByUser_IdOrderByLastSeenAtDesc(Long userId);

    Optional<UserDevice> findByToken(String token);

    long countByUser_Id(Long userId);

    Optional<UserDevice> findFirstByUser_IdOrderByLastSeenAtAsc(Long userId);

    void deleteByToken(String token);
}
