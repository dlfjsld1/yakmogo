package com.yakmogo.yakmogo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:yakmogo;MODE=MariaDB;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"scheduling.enabled=false",
	"telegram.bot.enabled=false",
	"telegram.bot.token=test-token",
	"telegram.bot.username=test-bot",
	"telegram.bot.chat-id=test-chat",
	"admin.password=test-admin",
	"app.frontend.url=http://localhost"
})
class YakmogoApplicationTests {

	@Test
	void contextLoads() {
	}

}
