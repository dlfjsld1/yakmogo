package com.yakmogo.yakmogo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.yakmogo.yakmogo.domain.MedicineGroup;

public interface MedicineGroupRepository extends JpaRepository<MedicineGroup, Long> {
	// 복용을 중단하지 않은 약들만 가져오기
	List<MedicineGroup> findAllByIsActiveTrue();
}
