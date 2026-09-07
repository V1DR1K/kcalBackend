package com.scalegrams.sharing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealShareRepository extends JpaRepository<MealShare, Long> {
    @Query("select share from MealShare share where share.tokenHash = :tokenHash")
    Optional<MealShare> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select share from MealShare share where share.tokenHash = :tokenHash")
    Optional<MealShare> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
