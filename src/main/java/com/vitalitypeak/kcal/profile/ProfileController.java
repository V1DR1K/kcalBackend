package com.vitalitypeak.kcal.profile;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.profile.ProfileDtos.ProfileResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpdateProfileRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @GetMapping
    ProfileResponse get(Authentication authentication) {
        return profileService.get(currentUser.from(authentication));
    }

    @PatchMapping
    ProfileResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(currentUser.from(authentication), request);
    }
}
