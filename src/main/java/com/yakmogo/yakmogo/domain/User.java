package com.yakmogo.yakmogo.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	// 유저가 삭제되면 '약 그룹'도 같이 삭제
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore // JSON 변환 시 무시 (무한루프 방지)
	private List<MedicineGroup> medicineGroups = new ArrayList<>();

	// 유저가 삭제되면 '복용 기록'도 같이 삭제
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<IntakeLog> intakeLogs = new ArrayList<>();

	@Builder
	public User(String name) {
		this.name = name;
	}

	public void addGuardian(Guardian guardian) {
		this.guardians.add(guardian);
	}
}
