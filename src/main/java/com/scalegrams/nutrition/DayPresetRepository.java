package com.scalegrams.nutrition;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface DayPresetRepository extends JpaRepository<DayPreset, Long> {
    List<DayPreset> findByUserAndDeletedAtIsNullOrderByUpdatedAtDesc(AppUser user);

    Optional<DayPreset> findByIdAndUserAndDeletedAtIsNull(Long id, AppUser user);

    @Query("select count(p) > 0 from DayPreset p where p.user = :user and lower(p.name) = lower(:name) and p.deletedAt is null")
    boolean existsActiveName(@Param("user") AppUser user, @Param("name") String name);
}
