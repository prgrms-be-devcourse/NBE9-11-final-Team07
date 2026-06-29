package com.back.popspot.domain.user.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {
	@Column(length = 100)
	private String email;

	@Column(length = 50)
	private String name;

	private User(String email, String name) {
		this.email = email;
		this.name = name;
	}

	public static User create(String email, String name) {
		return new User(email, name);
	}
}
