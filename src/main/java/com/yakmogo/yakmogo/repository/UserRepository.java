package com.yakmogo.yakmogo.repository;

import com.yakmogo.yakmogo.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	// 유저를 찾을 때 'guardians' 리스트도 Eager로 가져오기
	@Query("SELECT u FROM User u LEFT JOIN FETCH u.guardians WHERE u.id = :id")
	Optional<User> findByIdWithGuardians(@Param("id") Long id);
}
