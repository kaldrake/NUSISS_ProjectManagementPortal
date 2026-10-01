package com.example.demo.repository;

import com.example.demo.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByJwtToken(String jwtToken);

    boolean existsByJwtTokenAndExpiresAtAfter(String jwtToken, LocalDateTime now);

    // Derived deletes need a transaction; callers such as AuthController.logout have none
    @Transactional
    void deleteByJwtToken(String jwtToken);

    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    void deleteExpiredSessions(LocalDateTime now);
}
