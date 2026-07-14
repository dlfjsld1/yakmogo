package com.yakmogo.yakmogo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:yakmogo;MODE=MariaDB;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=validate",
	"scheduling.enabled=false",
	"telegram.bot.enabled=false",
	"telegram.bot.token=test-token",
	"telegram.bot.username=test-bot",
	"telegram.bot.chat-id=test-chat",
	"admin.password=test-admin",
	"auth.token.secret=test-auth-secret-with-at-least-32-bytes",
	"app.frontend.url=http://localhost"
})
class YakmogoApplicationTests {

	@Test
	void contextLoads() {
	}

}
