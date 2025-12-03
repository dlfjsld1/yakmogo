package com.yakmogo.yakmogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;

@EnableScheduling
@SpringBootApplication
public class YakmogoApplication {

	public static void main(String[] args) {
		SpringApplication.run(YakmogoApplication.class, args);
	}

	// Lazy Loading 문제 해결
	@Bean
	public Hibernate5JakartaModule hibernate5Module() {
		return new Hibernate5JakartaModule();
	}

}
