package com.yakmogo.yakmogo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yakmogo.yakmogo.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

}
