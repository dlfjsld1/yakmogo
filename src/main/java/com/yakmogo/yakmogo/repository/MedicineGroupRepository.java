package com.yakmogo.yakmogo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.User;

public interface MedicineGroupRepository extends JpaRepository<MedicineGroup, Long> {
	// 복용을 중단하지 않은 약들만 가져오기
	List<MedicineGroup> findAllByIsActiveTrue();

	// 같은 유저의 같은 약 중복 확인
	boolean existsByUserAndName(User user, String name);

	// 특정 유저의 약 목록 조회
	List<MedicineGroup> findAllByUserIdAndIsActiveTrue(Long userId);
}
