package com.yakmogo.yakmogo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class TelegramSender {

	@Value("${telegram.bot.token}")
	private String botToken;

	public void send(String chatId, String text) {
		try {
			String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
			URL url = URI.create(urlString).toURL();

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);

			String jsonInputString = String.format("{\"chat_id\": \"%s\", \"text\": \"%s\"}", chatId, text);

			try(OutputStream os = conn.getOutputStream()) {
				byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}

			//요청 실행(결과는 로그 생략)
			conn.getResponseCode();
			System.out.println("[텔레그램 메시지 발송] To: " + chatId + ", Msg: " + text);
		} catch (Exception e) {
			System.err.println("[텔레그램 메시지 발송] 전송실패: " + e.getMessage());
		}
	}
}
