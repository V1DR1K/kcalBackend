package com.scalegrams.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.scalegrams.user.AppUser;

public record UserPrincipal(AppUser user, String centralRole) implements UserDetails {
    public UserPrincipal(AppUser user) { this(user, null); }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + (centralRole == null ? user.getRole().name() : centralRole)));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getAuthUserId().toString();
    }
}
