package com.scalegrams.nutrition;

import org.springframework.http.HttpStatus;

import com.scalegrams.common.BadRequestException;

public class AiProviderException extends BadRequestException {
    private final String code;
    private final HttpStatus status;

    public AiProviderException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
