package com.scalegrams.security;

import java.util.List;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            @Value("${app.security.public-prometheus:false}") boolean publicPrometheus) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/health", "/api/version").permitAll()
                            .requestMatchers("/actuator/health").permitAll();
                    if (publicPrometheus) {
                        auth.requestMatchers("/actuator/prometheus").permitAll();
                    } else {
                        auth.requestMatchers("/actuator/prometheus").hasRole("ADMIN");
                    }
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN")
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/foods", "/api/foods/*/image").authenticated()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String origins,
            @Value("${app.cors.allowed-origin-patterns:https://localhost:*,https://127.0.0.1:*,https://192.168.*.*:*,http://localhost:*,http://127.0.0.1:*,http://192.168.*.*:*}") String originPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(csv(origins));
        configuration.setAllowedOriginPatterns(csv(originPatterns));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
