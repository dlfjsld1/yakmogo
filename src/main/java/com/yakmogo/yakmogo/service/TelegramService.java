package com.yakmogo.yakmogo.service;

import com.yakmogo.yakmogo.auth.AuthTokenService;
import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
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

	private final AuthTokenService authTokenService;
	private final RestTemplate restTemplate;

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

	public TelegramDeliveryResult sendMedicationAlert(
		IntakeLog intakeLog,
		Guardian guardian,
		MedicationAlert alert
	) {
		return sendPhotoWithButton(
			guardian.getChatId(),
			photoUrl(alert),
			caption(intakeLog, alert),
			intakeLog.getId(),
			guardian.getChatId()
		);
	}

	private TelegramDeliveryResult sendPhotoWithButton(
		String chatId,
		String photoUrl,
		String caption,
		Long logId,
		String guardianChatId
	) {
		String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";

		Map<String, Object> request = new HashMap<>(); // 문자열에서 Object로 변경
		request.put("chat_id", chatId);
		request.put("photo", photoUrl);
		request.put("caption", caption);
		request.put("reply_markup", createInlineKeyboard(logId, guardianChatId));

		try {
			restTemplate.postForEntity(url, request, String.class);
			log.info("텔레그램 버튼 이미지 전송 성공! (LogID: {})", logId);
			return TelegramDeliveryResult.success();
		} catch (Exception e) {
			log.warn("텔레그램 버튼 이미지 전송 실패 (LogID: {}, errorType: {})", logId, e.getClass().getSimpleName());
			return TelegramDeliveryResult.failure(e.getClass().getSimpleName());
		}
	}

	private String photoUrl(MedicationAlert alert) {
		String fileName = switch (alert.level()) {
			case ON_TIME -> "level0_alert.jpg";
			case LEVEL_1 -> "level1_alert.jpg";
			case LEVEL_2 -> "level2_alert.jpg";
			case LEVEL_3 -> "level3_alert.jpg";
		};
		return "https://github.com/dlfjsld1/yakmogo/blob/main/src/main/resources/static/images/" + fileName + "?raw=true";
	}

	private String caption(IntakeLog intakeLog, MedicationAlert alert) {
		String userName = intakeLog.getUser().getName();
		String medicineName = intakeLog.getMedicineGroup().getName();
		return switch (alert.level()) {
			case ON_TIME -> String.format(
				"🦉 [약모고 정시 알림]\n\n지금은 %s님이 '%s'을(를) 드실 시간입니다! 💊\n복용 후 아래 버튼을 눌러주세요.",
				userName, medicineName
			);
			case LEVEL_1 -> String.format(
				"🦉 [경고: 30분 경과]\n\n%s님... '%s' 아직 안 드셨습니까?\n복용 후 아래 버튼을 눌러주세요.",
				userName, medicineName
			);
			case LEVEL_2 -> String.format(
				"🔥 [긴급: 1시간 경과]\n\n%s님, '%s' 복용 시간이 한 시간 지났습니다.\n복용 후 아래 버튼을 눌러주세요.",
				userName, medicineName
			);
			case LEVEL_3 -> String.format(
				"🔥🔥 [반복 알림: %d시간 경과]\n\n%s님, '%s' 복용 시간이 %d시간 지났습니다.\n복용 후 아래 버튼을 눌러주세요.",
				alert.hoursOverdue(), userName, medicineName, alert.hoursOverdue()
			);
		};
	}

	private String createInlineKeyboard(Long logId, String guardianChatId) {
		String loginProof = authTokenService.issueLoginProof(guardianChatId);
		String magicLink = frontendUrl + "/tg-login?proof=" + loginProof;

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
		String text = String.format("당신의 챗 아이디는 <code>" + chatId + "</code> 입니다.\n\n" +
				"이 번호를 복사해서 관리자에게 알려주세요!");
		request.put("text", text);
		request.put("parse_mode", "HTML");
		try {
			restTemplate.postForEntity(url, request, String.class);
		} catch (Exception e) {
			log.error("Chat ID 안내 실패!", e);
		}
	}
}
