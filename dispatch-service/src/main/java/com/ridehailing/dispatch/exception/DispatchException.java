package com.ridehailing.dispatch.exception;

public class DispatchException extends RuntimeException {

    private final String code;

    public DispatchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
