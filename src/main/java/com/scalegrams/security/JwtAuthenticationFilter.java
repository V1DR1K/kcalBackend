package com.scalegrams.security;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.scalegrams.auth.CentralJwtService;
import com.scalegrams.user.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final CentralJwtService jwtService;
    private final UserRepository users;
    private final String accessCookieName;

    public JwtAuthenticationFilter(CentralJwtService jwtService, UserRepository users,
            @Value("${app.auth.cookies.access-name:scalegrams_access}") String accessCookieName) {
        this.jwtService = jwtService;
        this.users = users;
        this.accessCookieName = accessCookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : cookieToken(request);
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            UUID authUserId = jwtService.subject(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserPrincipal userDetails = users.findByAuthUserId(authUserId)
                        .map(UserPrincipal::new)
                        .orElseThrow(() -> new IllegalArgumentException("Usuario local no provisionado."));
                var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (RuntimeException ex) {
            logger.debug("No se pudo autenticar el JWT recibido: " + ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private String cookieToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (accessCookieName.equals(cookie.getName())) return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
