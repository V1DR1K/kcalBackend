package com.scalegrams.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.auth.AuthDtos.AuthResponse;
import com.scalegrams.auth.AuthDtos.LoginRequest;
import com.scalegrams.auth.AuthDtos.RegisterRequest;
import com.scalegrams.auth.AuthDtos.UserSummary;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.security.JwtService;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final int refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
            JwtService jwtService, RefreshTokenRepository refreshTokens,
            @Value("${app.jwt.refresh-token-days:30}") int refreshTokenDays) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Ya existe una cuenta con ese email.");
        }
        AppUser user = new AppUser();
        user.setFullName(request.fullName());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setWeightKg(request.weightKg());
        user.setHeightCm(request.heightCm());
        user.setBirthDate(request.birthDate());
        user.setGender(request.gender());
        user.setGoal(request.goal());
        user.setActivityLevel(request.activityLevel());
        NutritionGoalCalculator.apply(user);
        users.save(user);
        return createSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        AppUser user = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        return createSession(user);
    }

    @Transactional
    public AuthResponse rotate(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BadRequestException("Sesión inválida. Volvé a ingresar."));
        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            revokeAll(token.getUser());
            throw new BadRequestException("Sesión expirada. Volvé a ingresar.");
        }
        AppUser user = token.getUser();
        token.setRevokedAt(OffsetDateTime.now());
        refreshTokens.save(token);
        return createSession(user);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokens.save(token);
        });
    }

    @Transactional
    public void revokeAll(AppUser user) {
        refreshTokens.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(token -> {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokens.save(token);
        });
    }

    @Transactional
    public AuthResponse changePassword(AppUser user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual es incorrecta.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        revokeAll(user);
        return createSession(user);
    }

    private AuthResponse createSession(AppUser user) {
        byte[] raw = new byte[48];
        secureRandom.nextBytes(raw);
        String refreshValue = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(refreshValue));
        token.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenDays));
        refreshTokens.save(token);

        return new AuthResponse(jwtService.generate(user), "Bearer", refreshValue,
                new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getPlanName(), user.getRole()));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}