package com.scalegrams.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CentralJwtTestSupport {
    private CentralJwtTestSupport() {}

    public static void stubIdentity(CentralJwtService centralJwt) {
        when(centralJwt.identity(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0, String.class);
            UUID userId = UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8));
            return new CentralJwtService.Identity(userId, "scalegrams", null);
        });
        when(centralJwt.subject(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0, String.class);
            return UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8));
        });
    }
}
