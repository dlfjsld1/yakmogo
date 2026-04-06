package com.yakmogo.yakmogo.service;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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

	private final IntakeLogRepository intakeLogRepository;
	private final TelegramService telegramService;

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
		// 1. 유저가 메시지를 보냈을 때 (/start 등)
		if (update.hasMessage() && update.getMessage().hasText()) {
			String messageText = update.getMessage().getText();
			String chatId = String.valueOf(update.getMessage().getChatId());

			if ("/start".equals(messageText)) {
				sendChatIdResponse(chatId);
			}
		}
		// 2. 유저가 인라인 버튼을 눌렀을 때 (callback_query)
		else if (update.hasCallbackQuery()) {
			handleCallback(update);
		}
	}

	private void handleCallback(Update update) {
		String callbackData = update.getCallbackQuery().getData();
		String chatId = String.valueOf(update.getCallbackQuery().getMessage().getChatId());
		String callbackQueryId = update.getCallbackQuery().getId();

		// A. 내 챗 아이디 확인 요청 처리
		if ("GET_MY_ID".equals(callbackData)) {
			telegramService.sendChatIdInfo(chatId);
			answerCallbackQuery(callbackQueryId);
			log.info("[ID 확인 요청] ChatID: {}", chatId);
			return;
		}

		// B. 기존 "TAKEN_로그ID" 복용 완료 처리
		if (callbackData.startsWith("TAKEN_")) {
			processMedicineTaken(callbackData, chatId, callbackQueryId);
		}
	}

	private void processMedicineTaken(String callbackData, String chatId, String callbackQueryId) {
		try {
			Long logId = Long.parseLong(callbackData.split("_")[1]);
			IntakeLog logInfo = intakeLogRepository.findByIdWithUserAndGroup(logId).orElse(null);

			// 약을 아직 안 먹은 상태(PENDING)일 때만 처리
			if (logInfo != null && logInfo.getStatus() == IntakeStatus.PENDING) {
				// 상태 TAKEN으로 바꾸고 실제 먹은 시간까지 기록
				logInfo.markAsTaken();
				intakeLogRepository.save(logInfo);

				String msg = String.format("✅ %s님의 오늘 '%s' 복용이 확인되었습니다!\n%s 에 복용하셨어요.\n저는 이제 퇴근할게요! 💤",
					logInfo.getUser().getName(),
					logInfo.getMedicineGroup().getName(),
					logInfo.getActualTakenTime().toLocalTime().toString().substring(0, 5));

				telegramService.sendMessage(chatId, msg);
				log.info("[약 복용 확인] LogID: {} 처리 완료", logId);
			}

			// 텔레그램 버튼의 '로딩(모래시계)' 표시 끄기
			answerCallbackQuery(callbackQueryId);

		} catch (Exception e) {
			log.error("버튼 콜백 처리 중 에러 발생", e);
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

	private void answerCallbackQuery(String callbackQueryId) {
		AnswerCallbackQuery answer = new AnswerCallbackQuery();
		answer.setCallbackQueryId(callbackQueryId);
		try {
			execute(answer);
		} catch (TelegramApiException e) {
			log.error("콜백 쿼리 응답 실패", e);
		}
	}
}