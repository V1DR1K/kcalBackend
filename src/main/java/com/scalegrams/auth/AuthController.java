package com.scalegrams.auth;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.auth.AuthDtos.AuthResponse;
import com.scalegrams.auth.AuthDtos.ChangePasswordRequest;
import com.scalegrams.auth.AuthDtos.LoginRequest;
import com.scalegrams.auth.AuthDtos.RefreshRequest;
import com.scalegrams.auth.AuthDtos.UserSummary;
import com.scalegrams.common.CurrentUser;
import com.scalegrams.user.AppUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final boolean secureCookies;
    private final Duration accessCookieMaxAge;
    private final Duration refreshCookieMaxAge;

    public AuthController(AuthService authService, CurrentUser currentUser,
            @Value("${app.auth.cookies.access-name:scalegrams_access}") String accessCookieName,
            @Value("${app.auth.cookies.refresh-name:scalegrams_refresh}") String refreshCookieName,
            @Value("${app.auth.cookies.secure:false}") boolean secureCookies,
            @Value("${app.auth.cookies.access-max-age:15m}") Duration accessCookieMaxAge,
            @Value("${app.auth.cookies.refresh-max-age:30d}") Duration refreshCookieMaxAge) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.secureCookies = secureCookies;
        this.accessCookieMaxAge = accessCookieMaxAge;
        this.refreshCookieMaxAge = refreshCookieMaxAge;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return issueSession(authService.login(request), response);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String refreshToken = cookieValue(httpRequest, refreshCookieName);
        if (refreshToken == null && request != null) refreshToken = request.refreshToken();
        return issueSession(authService.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    void logout(@RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String refreshToken = cookieValue(httpRequest, refreshCookieName);
        if (refreshToken == null && request != null) refreshToken = request.refreshToken();
        authService.logout(refreshToken);
        clearCookie(response, accessCookieName, "/");
        clearCookie(response, refreshCookieName, "/api/auth");
    }

    @GetMapping("/me")
    UserSummary me(Authentication authentication) {
        return authService.summary(currentUser.from(authentication));
    }

    @PutMapping("/change-password")
    AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication,
            HttpServletRequest httpRequest) {
        AppUser user = currentUser.from(authentication);
        String authorization = httpRequest.getHeader("Authorization");
        String accessToken = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7)
                : cookieValue(httpRequest, accessCookieName);
        return authService.changePassword(user, accessToken, request.currentPassword(), request.newPassword());
    }

    private AuthResponse issueSession(AuthResponse session, HttpServletResponse response) {
        addCookie(response, accessCookieName, session.accessToken(), "/", accessCookieMaxAge);
        addCookie(response, refreshCookieName, session.refreshToken(), "/api/auth", refreshCookieMaxAge);
        return session.withoutTokens();
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, URLEncoder.encode(value, StandardCharsets.UTF_8))
                .httpOnly(true).secure(secureCookies).sameSite("Strict").path(path).maxAge(maxAge).build().toString());
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, "")
                .httpOnly(true).secure(secureCookies).sameSite("Strict").path(path).maxAge(Duration.ZERO).build().toString());
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
