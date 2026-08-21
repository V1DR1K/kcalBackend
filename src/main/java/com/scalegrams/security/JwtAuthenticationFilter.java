package com.scalegrams.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
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

    public JwtAuthenticationFilter(CentralJwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
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
}
