package com.scalegrams.auth;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.auth.AuthDtos.AuthResponse;
import com.scalegrams.auth.AuthDtos.LoginRequest;
import com.scalegrams.auth.AuthDtos.UserSummary;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.Role;
import com.scalegrams.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final CentralAuthClient centralAuth;
    private final CentralJwtService centralJwt;
    private final Role defaultRole;

    private static final Map<String, String> LEGACY_EMAIL_BY_USERNAME = Map.of(
            "tomas", "tomicolombo20051@gmail.com",
            "avril", "avril@ejemplo.com",
            "balta", "balta@scalegrams.com");

    public AuthService(UserRepository users, CentralAuthClient centralAuth, CentralJwtService centralJwt,
            @org.springframework.beans.factory.annotation.Value("${app.auth.default-role:USER}") Role defaultRole) {
        this.users = users;
        this.centralAuth = centralAuth;
        this.centralJwt = centralJwt;
        this.defaultRole = defaultRole;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        CentralAuthClient.TokenResponse central = centralAuth.login(request.username(), request.password());
        return localSession(central);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadRequestException("La sesión de renovación no es válida.");
        }
        return localSession(centralAuth.refresh(rawRefreshToken));
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        centralAuth.logout(rawRefreshToken);
    }

    @Transactional
    public AuthResponse changePassword(AppUser user, String accessToken, String currentPassword, String newPassword) {
        centralAuth.changePassword(accessToken, currentPassword, newPassword);
        return new AuthResponse(null, "Bearer", null, null, false);
    }

    private AuthResponse localSession(CentralAuthClient.TokenResponse central) {
        UUID authUserId = centralJwt.subject(central.accessToken());
        if (!authUserId.equals(central.user().id())) {
            throw new BadRequestException("El token central no coincide con el usuario autenticado.");
        }
        AppUser user = provision(authUserId, central.user().username());
        return new AuthResponse(central.accessToken(), central.tokenType(), central.refreshToken(), summary(user, central.user().username()),
                central.user().mustChangePassword());
    }

    private AppUser provision(UUID authUserId, String username) {
        AppUser user = users.findByAuthUserId(authUserId).orElseGet(() -> {
            String legacyEmail = LEGACY_EMAIL_BY_USERNAME.get(username.trim().toLowerCase());
            return legacyEmail == null ? new AppUser() : users.findByEmailIgnoreCase(legacyEmail)
                    .orElseGet(AppUser::new);
        });
        if (user.getAuthUserId() != null && !user.getAuthUserId().equals(authUserId)) {
            throw new BadRequestException("La cuenta local ya está vinculada a otro usuario central.");
        }
        boolean newUser = user.getId() == null;
        user.setAuthUserId(authUserId);
        user.setPasswordHash(null);
        if (newUser) {
            user.setFullName(username);
            user.setRole(defaultRole);
        }
        return users.save(user);
    }

    private UserSummary summary(AppUser user, String username) {
        return new UserSummary(user.getId(), username, user.getFullName(), user.getEmail(), user.getPlanName(), user.getRole());
    }

    public UserSummary summary(AppUser user) {
        return summary(user, user.getAuthUserId() == null ? null : user.getAuthUserId().toString());
    }
}
