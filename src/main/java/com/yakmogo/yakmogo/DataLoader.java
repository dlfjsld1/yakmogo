package com.yakmogo.yakmogo;

import com.yakmogo.yakmogo.domain.*;
import com.yakmogo.yakmogo.repository.*;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
@Profile("dev")
public class DataLoader implements CommandLineRunner {

	private final UserRepository userRepository;
	private final MedicineGroupRepository medicineGroupRepository;
	private final IntakeLogRepository intakeLogRepository;
	private final Clock applicationClock;

	@Value("${telegram.bot.chat-id}")
	private String myChatIdForTest;

	@Override
	public void run(String... args) throws Exception {
		// 데이터가 없으면 초기 데이터 생성
		if (userRepository.count() == 0) {
			System.out.println("============== [테스트 데이터 생성 중] ==============");

			// 1. 유저 생성
			User grandpa = User.builder()
				.name("김철수")
				.build();
			userRepository.save(grandpa);

			// 2. 알림 수신자 생성
			Guardian myGuardian = Guardian.builder()
				.user(grandpa)
				.name("알림수신자")
				.chatId(myChatIdForTest)
				.build();

			// 3. 관계 연결
			grandpa.addGuardian(myGuardian);

			// 4. 저장
			userRepository.save(grandpa);

			// 5. 약 스케줄 생성 (매일 아침 9시)
			MedicineGroup medicine = MedicineGroup.builder()
				.user(grandpa)
				.name("고혈압약")
				.scheduleType(ScheduleType.DAILY)
				.intakeTime(LocalTime.of(9, 0)) // 아침 9시 복용
				.startDate(LocalDate.now(applicationClock))
				.build();
			medicineGroupRepository.save(medicine);

			// 6. 오늘 치 기록 강제 생성 (아침 9시 거를 안 먹은 상태)
			// 1시간 경과 후 로직 발동
			IntakeLog log = IntakeLog.builder()
				.user(grandpa)
				.medicineGroup(medicine)
				.intakeDate(LocalDate.now(applicationClock))
				.intakeTime(LocalTime.of(9, 0)) // 09:00 예정
				.status(IntakeStatus.PENDING)   // 아직 안 먹음
				.build();
			intakeLogRepository.save(log);

			System.out.println("============== [테스트 데이터 생성 완료] ==============");
			System.out.println("👉 상황: 현재 시간은 오후인데, 아침 9시 약을 아직 안 먹음 (PENDING)");
			System.out.println("👉 예상 결과: 스케줄러가 돌면서 텔레그램 알림 발송");
		}
	}
}
