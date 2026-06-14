package com.back.popspot.domain.popupStore.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.popupStore.entity.PopupStore;

public interface PopupStoreRepository extends JpaRepository<PopupStore, Long> {
}
