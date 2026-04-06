package com.yakmogo.yakmogo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotHandler extends TelegramLongPollingBot {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${telegram.bot.username}")
	private String botUsername;

	@Override
	public String getBotUsername() {
		return botUsername;
	}

	@Override
	public String getBotToken() {
		return botToken;
	}

	@Override
	public void onUpdateReceived(Update update) {
		// 유저가 메시지를 보냈을 때 (시작 버튼 포함)
		if (update.hasMessage() && update.getMessage().hasText()) {
			String messageText = update.getMessage().getText();
			String chatId = String.valueOf(update.getMessage().getChatId());
			// 유저가 /start를 눌렀을 때
			if ("/start".equals(messageText)) {
				sendChatIdResponse(chatId);
			}
		}
	}

	private void sendChatIdResponse(String chatId) {
		SendMessage message = new SendMessage();
		message.setChatId(chatId);
		message.setParseMode("HTML");
		message.setText("당신의 챗 아이디는 <code>" + chatId + "</code> 입니다.\n\n" +
			"이 번호를 복사해서 관리자에게 알려주세요!");

		try {
			execute(message);
			log.info("Chat ID 발송 완료: {}", chatId);
		} catch (TelegramApiException e) {
			log.error("텔레그램 응답 중 에러 발생!", e);
		}
	}
}