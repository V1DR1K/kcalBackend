package com.scalegrams.nutrition;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

import jakarta.persistence.LockModeType;

public interface AiCaptureRepository extends JpaRepository<AiCapture, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select capture from AiCapture capture where capture.id = :id and capture.user = :user")
    Optional<AiCapture> findOwnedForUpdate(@Param("id") UUID id, @Param("user") AppUser user);
}
