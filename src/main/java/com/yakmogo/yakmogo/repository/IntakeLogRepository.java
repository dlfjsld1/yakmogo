package com.yakmogo.yakmogo.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.MedicineGroup;

public interface IntakeLogRepository extends JpaRepository<IntakeLog, Long> {

	// 감시자가 호출할 쿼리
	// User와 MedicineGroup을 한번에 가져와서 (Join Fetch)를 해서 성능 저하 막음
	@Query(
		"SELECT il FROM IntakeLog il " +
		"JOIN FETCH il.user " +
		"JOIN FETCH il.medicineGroup " +
		"WHERE il.intakeDate = :today " +
		"AND il.status = 'pending' " +
		"AND il.intakeTime <= :nowTime")
	List<IntakeLog> findPendingLogs(
		@Param("today") LocalDate today,
		@Param("nowTime") LocalTime nowTime
	);

	//중복 방지(이미 오늘 만들었는지 확인)
	boolean existsByMedicineGroupAndIntakeDate(
		MedicineGroup medicineGroup,
		LocalDate intakeDate
		);

	// 특정 유저의 오늘 먹을 약 조회
	List<IntakeLog> findByUserIdAndIntakeDate(
		Long userId,
		LocalDate intakeDate
	);
}
