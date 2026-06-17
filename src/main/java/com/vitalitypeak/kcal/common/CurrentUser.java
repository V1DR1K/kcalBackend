package com.vitalitypeak.kcal.common;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.vitalitypeak.kcal.security.UserPrincipal;
import com.vitalitypeak.kcal.user.AppUser;

@Component
public class CurrentUser {
    public AppUser from(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).user();
    }
}
