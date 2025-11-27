package com.yakmogo.yakmogo.service;

import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.dto.UserCreateRequest;
import com.yakmogo.yakmogo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;

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
}