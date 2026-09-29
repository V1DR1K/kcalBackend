package com.scalegrams.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieOriginValidationFilter extends OncePerRequestFilter {
    private final CorsConfigurationSource cors;
    private final Set<String> cookieNames;

    public CookieOriginValidationFilter(@Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            @Value("${app.auth.cookies.access-name:scalegrams_access}") String accessName,
            @Value("${app.auth.cookies.refresh-name:scalegrams_refresh}") String refreshName) {
        this.cors = cors;
        this.cookieNames = Set.of(accessName, refreshName);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String origin = request.getHeader("Origin");
        if (origin != null && !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method) && hasAuthCookie(request)
                && !isAllowedOrigin(request, origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origen no permitido para una sesión con cookies.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        return Arrays.stream(request.getCookies()).anyMatch(cookie -> cookieNames.contains(cookie.getName()));
    }

    private boolean isAllowedOrigin(HttpServletRequest request, String origin) {
        CorsConfiguration configuration = cors.getCorsConfiguration(request);
        return configuration != null && configuration.checkOrigin(origin) != null;
    }
}
