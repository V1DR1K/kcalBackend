package com.scalegrams.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.scalegrams.common.BadRequestException;

@Component
public class CentralAuthClient {
    private final RestClient client;

    public CentralAuthClient(RestClient.Builder builder, @Value("${app.auth.service-url}") String serviceUrl) {
        String baseUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.client = builder.baseUrl(baseUrl).build();
    }

    public TokenResponse login(String username, String password) {
        return post("/api/login", new Credentials(username, password), TokenResponse.class);
    }

    public TokenResponse refresh(String refreshToken) {
        return post("/api/refresh", new RefreshRequest(refreshToken), TokenResponse.class);
    }

    public void logout(String refreshToken) {
        post("/api/logout", new RefreshRequest(refreshToken), MessageResponse.class);
    }

    public void changePassword(String accessToken, String currentPassword, String newPassword) {
        try {
            client.post().uri("/api/change-password")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(new ChangePasswordRequest(currentPassword, newPassword))
                    .retrieve().body(MessageResponse.class);
        } catch (RestClientResponseException ex) {
            throw new BadRequestException(messageFor(ex.getStatusCode()));
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return client.post().uri(path).body(body).retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw new BadRequestException(messageFor(ex.getStatusCode()));
        }
    }

    private String messageFor(HttpStatusCode status) {
        return status.value() == 401 ? "Usuario o contraseña incorrectos." : "No se pudo contactar al servicio de autenticación.";
    }

    private record Credentials(String username, String password) {}
    private record RefreshRequest(String refreshToken) {}
    private record ChangePasswordRequest(String currentPassword, String newPassword) {}
    private record MessageResponse(String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, CentralUser user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CentralUser(UUID id, String username, boolean mustChangePassword) {}
}
