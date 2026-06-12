package com.back.popspot.domain.user.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseEntity {
	@Column(length = 100)
	private String email;

	@Column(length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;
}
