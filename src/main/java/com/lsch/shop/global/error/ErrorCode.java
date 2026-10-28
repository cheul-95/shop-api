package com.lsch.shop.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "0000", "시스템 에러입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "0001", "입력값이 올바르지 않습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "0002", "요청 형식이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "0003", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "0004", "지원하지 않는 HTTP 메서드입니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST,"0005","재고가 부족합니다"),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST,"0006","수량은 1 이상이어야 합니다");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
