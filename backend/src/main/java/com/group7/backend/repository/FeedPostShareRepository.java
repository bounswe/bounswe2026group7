package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedPostShareRepository extends JpaRepository<FeedPostShare, Long> {
    long countByPostId(Long postId);
}
