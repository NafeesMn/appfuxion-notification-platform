package com.appfuxion_notification_platform.backend;

import org.springframework.boot.SpringApplication;

public class TestNotificationPlatformBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(NotificationPlatformBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
