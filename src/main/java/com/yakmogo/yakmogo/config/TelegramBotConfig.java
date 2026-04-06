package com.yakmogo.yakmogo.config;

import com.yakmogo.yakmogo.service.TelegramBotHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

	@Bean
	public TelegramBotsApi telegramBotsApi(TelegramBotHandler handler) throws TelegramApiException {
		// 봇 세션을 관리하는 API 객체 생성
		TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);

		try {
			// 핸들러를 API에 등록 (이때 실제 서버 연결 시작)
			api.registerBot(handler);
			System.out.println("✅ [Success] 텔레그램 봇이 성공적으로 등록되었습니다!");
		} catch (TelegramApiException e) {
			System.err.println("❌ [Error] 봇 등록 중 에러 발생: " + e.getMessage());
			throw e;
		}

		return api;
	}
}
