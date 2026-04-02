package com.yakmogo.yakmogo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${app.frontend.url}")
	private String frontendUrl;

	private final RestTemplate restTemplate = new RestTemplate();

	// 기존 텍스트 전송 (버튼 없음)
	public void sendMessage(String chatId, String text) {
		String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
		Map<String, String> request = new HashMap<>();
		request.put("chat_id", chatId);
		request.put("text", text);
		try {
			restTemplate.postForEntity(url, request, String.class);
		} catch (Exception e) {
			log.error("텔레그램 메시지 전송 실패!", e);
		}
	}

	// 이미지 + 인라인 버튼을 같이 전송
	public void sendPhotoWithButton(String chatId, String photoUrl, String caption, Long logId, String guardianChatId) {
		String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";

		Map<String, Object> request = new HashMap<>(); // 문자열에서 Object로 변경
		request.put("chat_id", chatId);
		request.put("photo", photoUrl);
		request.put("caption", caption);
		request.put("reply_markup", createInlineKeyboard(logId, guardianChatId));

		try {
			restTemplate.postForEntity(url, request, String.class);
			log.info("텔레그램 버튼 이미지 전송 성공! (LogID: {})", logId);
		} catch (Exception e) {
			log.error("텔레그램 버튼 이미지 전송 실패!", e);
		}
	}

	private String createInlineKeyboard(Long logId, String guardianChatId) {
		String magicLink = frontendUrl + "/tg-login?chatId=" + guardianChatId;

		// 말풍선 밑에 달릴 인라인 버튼 JSON 형태
		// callback_data에 "TAKEN_15" 처럼 복용기록 ID를 심어둠
		// 인라인 키보드: [복용 완료] / [상세 정보] / [내 ID 확인]
		return String.format(
			"{\"inline_keyboard\": [" +
				"[{\"text\": \"🆔 내 Chat ID 확인\", \"callback_data\": \"GET_MY_ID\"}]," +
				"[{\"text\": \"🔍 상세 정보 확인\", \"url\": \"%s\"}]," +
				"[{\"text\": \"💊 ✅ [약 복용 완료]\", \"callback_data\": \"TAKEN_%d\"}]" +
				"]}",
			magicLink, logId
		);
	}

	public void sendChatIdInfo(String chatId) {
		String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
		Map<String, Object> request = new HashMap<>();
		request.put("chat_id", chatId);
		String text = String.format("Your Chat ID is: <code>%s</code>\n\n위 번호를 눌러 복사해주세요! 💊", chatId);
		request.put("text", text);
		request.put("parse_mode", "HTML");
		try {
			restTemplate.postForEntity(url, request, String.class);
		} catch (Exception e) {
			log.error("Chat ID 안내 실패!", e);
		}
	}
}