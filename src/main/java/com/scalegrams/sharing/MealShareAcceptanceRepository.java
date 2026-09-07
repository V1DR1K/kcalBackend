package com.scalegrams.sharing;

import java.util.Optional;

import com.scalegrams.user.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealShareAcceptanceRepository extends JpaRepository<MealShareAcceptance, Long> {
    Optional<MealShareAcceptance> findByShareIdAndRecipient(Long shareId, AppUser recipient);
}
