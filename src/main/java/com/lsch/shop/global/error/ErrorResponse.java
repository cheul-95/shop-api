package com.lsch.shop.global.error;

public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse( errorCode.name(), errorCode.getMessage());
    }
}
