package com.hotel.solr.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public BusinessException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
