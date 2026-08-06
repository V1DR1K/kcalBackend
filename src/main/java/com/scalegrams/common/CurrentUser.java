package com.scalegrams.common;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.scalegrams.security.UserPrincipal;
import com.scalegrams.user.AppUser;

@Component
public class CurrentUser {
    public AppUser from(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).user();
    }
}
