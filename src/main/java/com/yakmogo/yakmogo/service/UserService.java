package com.yakmogo.yakmogo.service;

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
			.guardianChatIds(request.guardianChatIds())
			.build();

		userRepository.save(user);

		System.out.println("유저 등록 완료!: " + user.getName());
		return user.getId();
	}
}