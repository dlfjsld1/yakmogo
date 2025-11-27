package com.yakmogo.yakmogo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String guardianChatIds;

	@Builder
	public User(String name, String guardianChatIds) {
		this.name = name;
		this.guardianChatIds = guardianChatIds;
	}
}
