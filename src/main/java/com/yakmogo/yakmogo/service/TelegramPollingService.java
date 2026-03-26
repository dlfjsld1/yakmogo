package com.yakmogo.yakmogo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus; // 본인 Enum 이름에 맞게 확인!
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPollingService {

	private final IntakeLogRepository intakeLogRepository;
	private final TelegramService telegramService;
	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${telegram.bot.token}")
	private String botToken;

	// 중복 처리 방지
	private long lastUpdateId = 0;

	// 3000ms마다 텔레그램 서버에 확인 요청
	@Scheduled(fixedDelay = 3000)
	@Transactional
	public void pollTelegramUpdates() {
		if (intakeLogRepository.countByStatus(IntakeStatus.PENDING) == 0) {
			return;
		}
		try {
			String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1);
			ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
			JsonNode root = response.getBody();

			if (root != null && root.path("ok").asBoolean()) {
				JsonNode result = root.path("result");

				for (JsonNode update : result) {
					lastUpdateId = update.path("update_id").asLong(); // 책갈피 업데이트

					// 누군가 '인라인 버튼'을 눌렀을 때 (callback_query)
					if (update.has("callback_query")) {
						JsonNode callbackQuery = update.path("callback_query");
						String callbackData = callbackQuery.path("data").asText(); // 버튼의 "TAKEN_15"
						String chatId = callbackQuery.path("message").path("chat").path("id").asText();
						String callbackQueryId = callbackQuery.path("id").asText();

						// 데이터 처리
						handleCallback(callbackData, chatId, callbackQueryId);
					}
				}
			}
		} catch (Exception e) {
			log.error("텔레그램 폴링 중 에러 발생: {}", e.getMessage());
		}
	}

	private void handleCallback(String callbackData, String chatId, String callbackQueryId) {
		// "TAKEN_로그ID" 형식인지 확인
		if (callbackData.startsWith("TAKEN_")) {
			try {
				Long logId = Long.parseLong(callbackData.split("_")[1]);
				IntakeLog logInfo = intakeLogRepository.findById(logId).orElse(null);

				// 약을 아직 안 먹은 상태(PENDING)일 때만 처리
				if (logInfo != null && logInfo.getStatus() == IntakeStatus.PENDING) {

					// 상태 TAKEN으로 바꾸고 실제 먹은 시간까지 기록
					logInfo.markAsTaken();
					intakeLogRepository.save(logInfo);

					// 2. 복용 확인 알림 전송
					String msg = String.format("✅ %s님의 '%s' 복용이 확인되었습니다!\n실제 복용 시간: %s\n저는 이제 퇴근할게요! 💤",
						logInfo.getUser().getName(),
						logInfo.getMedicineGroup().getName(),
						logInfo.getActualTakenTime().toLocalTime().toString().substring(0, 5)); // 14:30 형태로 출력
					telegramService.sendMessage(chatId, msg);
					log.info("[약 복용 확인] LogID: {} 처리 완료", logId);
				}

				// 3. 텔레그램 버튼의 '로딩(모래시계)' 표시 끄기
				answerCallbackQuery(callbackQueryId);

			} catch (Exception e) {
				log.error("버튼 콜백 처리 중 에러", e);
			}
		}
	}

	// 텔레그램 서버에 응답해주는 메서드
	private void answerCallbackQuery(String callbackQueryId) {
		String url = "https://api.telegram.org/bot" + botToken + "/answerCallbackQuery";
		Map<String, String> request = new HashMap<>();
		request.put("callback_query_id", callbackQueryId);
		try {
			restTemplate.postForEntity(url, request, String.class);
		} catch (Exception e) {
			log.error("콜백 쿼리 응답 실패", e);
		}
	}
}