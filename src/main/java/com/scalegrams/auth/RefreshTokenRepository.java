package com.scalegrams.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, OffsetDateTime now);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :revokedAt where token.id = :id and token.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :revokedAt where token.user.id = :userId and token.revokedAt is null")
    int revokeAllActive(@Param("userId") Long userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
