package com.lsch.shop.domain.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, BEARER, expiresIn);
    }
}