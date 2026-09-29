package com.scalegrams.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByAuthUserId(UUID authUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AppUser account where account.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
