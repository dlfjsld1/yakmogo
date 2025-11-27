package com.yakmogo.yakmogo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	// 알림 수신자 이름
	private String name;
	// 알림 수신자 텔레그램 ID
	private String chatId;

	@Builder
	public Guardian(User user, String name, String chatId) {
		this.user = user;
		this.name = name;
		this.chatId = chatId;
	}
}