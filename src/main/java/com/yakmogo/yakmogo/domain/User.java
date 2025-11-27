package com.yakmogo.yakmogo.domain;

import java.util.ArrayList;
import java.util.List;

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

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
	private List<Guardian> guardians = new ArrayList<>();

	@Builder
	public User(String name) {
		this.name = name;
	}

	public void addGuardian(Guardian guardian) {
		this.guardians.add(guardian);
	}
}
