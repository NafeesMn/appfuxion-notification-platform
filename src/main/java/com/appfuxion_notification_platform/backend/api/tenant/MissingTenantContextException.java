package com.appfuxion_notification_platform.backend.api.tenant;

import com.appfuxion_notification_platform.backend.api.common.ApiBadRequestException;

public class MissingTenantContextException extends ApiBadRequestException {

    public MissingTenantContextException(String headerName) {
        super("Missing required tenant header: " + headerName);
    }
}
