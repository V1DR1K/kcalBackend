package com.scalegrams.profile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scalegrams.user.AppUser;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {
    List<WeightEntry> findByUserOrderByEntryDateAsc(AppUser user);

    Optional<WeightEntry> findByUserAndEntryDate(AppUser user, LocalDate entryDate);

    Optional<WeightEntry> findByIdAndUser(Long id, AppUser user);
}