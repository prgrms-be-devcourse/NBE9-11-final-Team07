package com.back.popspot.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
