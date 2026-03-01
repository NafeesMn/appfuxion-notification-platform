package com.appfuxion_notification_platform.backend.delivery.scaling;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.ProviderDispatchResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public interface NotificationProviderGateway {

    ProviderDispatchResult dispatch(NotificationJob job, WorkerIdentity worker);
}
