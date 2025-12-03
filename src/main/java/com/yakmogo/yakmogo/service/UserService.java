package com.yakmogo.yakmogo.service;

import java.util.List;

import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.dto.ReceiverCreateRequest;
import com.yakmogo.yakmogo.dto.UserCreateRequest;
import com.yakmogo.yakmogo.repository.GuardianRepository;
import com.yakmogo.yakmogo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final GuardianRepository guardianRepository;

	// 유저 등록
	public Long registerUser(UserCreateRequest request) {
		User user = User.builder()
			.name(request.name())
			.build();

		// 알림 수신자 생성 및 연결
		if (request.guardians() != null) {
			for (UserCreateRequest.GuardianDto gDto : request.guardians()) {
				Guardian guardian = Guardian.builder()
					.user(user)
					.name(gDto.name())
					.chatId(gDto.chatId())
					.build();
				user.addGuardian(guardian); // 리스트에 추가
			}
		}

		userRepository.save(user);

		System.out.println("유저 등록 완료!: " + user.getName());
		return user.getId();
	}

	// 기존 유저에게 수신자 추가
	public void addReceiver(Long userId, ReceiverCreateRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));

		Guardian guardian = Guardian.builder()
			.user(user)
			.name(request.name())
			.chatId(request.chatId())
			.build();

		user.addGuardian(guardian);

		// Transactional 덕분에 user만 불러왔어도 receiver가 자동 저장됨 (Cascade)
	}

	// 수신자 삭제
	public void deleteReceiver(Long userId, Long receiverId) {
		// 삭제할 수신자 찾음
		Guardian guardian = guardianRepository.findById(receiverId)
			.orElseThrow(() -> new IllegalArgumentException("해당 수신자를 찾을 수 없습니다."));

		// 해당 유저의 수신자인지 확인
		if (!guardian.getUser().getId().equals(userId)) {
			throw new IllegalArgumentException("잘못된 요청입니다. (해당 유저의 수신자가 아닙니다)");
		}

		guardianRepository.delete(guardian);

		System.out.println("수신자 삭제 완료: ID=" + receiverId);
	}

	// 유저 삭제
	public void deleteUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));

		userRepository.delete(user); // Cascade
		System.out.println("복용자 및 관련 데이터 삭제 완료: " + user.getName());
	}

	// 유저 단건 조회 (알림 수신자 포함)
	public User getUser(Long userId) {
		return userRepository.findByIdWithGuardians(userId)
			.orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));
	}

	// 모든 가족 목록 가져오기
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}
}