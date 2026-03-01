package com.appfuxion_notification_platform.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class NotificationPlatformBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
