package com.vitalitypeak.kcal.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitalitypeak.kcal.auth.AuthDtos.AuthResponse;
import com.vitalitypeak.kcal.auth.AuthDtos.LoginRequest;
import com.vitalitypeak.kcal.auth.AuthDtos.RegisterRequest;
import com.vitalitypeak.kcal.auth.AuthDtos.UserSummary;
import com.vitalitypeak.kcal.common.BadRequestException;
import com.vitalitypeak.kcal.security.JwtService;
import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        AppUser user = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        return response(user);
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwtService.generate(user), "Bearer",
                new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getPlanName(), user.getRole()));
    }
}
