package com.terraguard.repo;

import com.terraguard.model.PendingReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingReviewRepository extends JpaRepository<PendingReview, Long> {
    Optional<PendingReview> findByRepoAndPrNumberAndSha(String repo, Integer prNumber, String sha);
}
