package com.yakmogo.yakmogo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("💊 약모고 (Yakmogo) API")
				.description("약 복용을 잊지 않게 도와주는 앱 API 명세서")
				.version("v1.0.0"));
	}
}