package com.yakmogo.yakmogo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.yakmogo.yakmogo.interceptor.AdminInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final AdminInterceptor adminInterceptor;

	//인터셉터 등록
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(adminInterceptor)
			.addPathPatterns("/api/**")
			// 텔레그램 인증 API는 암호 없이 통과
			.excludePathPatterns("/api/public/**", "/api/v1/auth/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins("http://localhost:5173") // 프론트 주소
			.allowedMethods("*")
			.allowedHeaders("*")
			.allowCredentials(true);
	}
}
