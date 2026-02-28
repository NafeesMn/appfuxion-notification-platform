package com.appfuxion_notification_platform.backend.api.common;

public class ApiBadRequestException extends RuntimeException {

    public ApiBadRequestException(String message) {
        super(message);
    }
}
