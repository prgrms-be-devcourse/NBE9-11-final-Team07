package com.back.popspot.domain.popupStore.repository;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopupStoreRepository extends JpaRepository<PopupStore, Long> {
}
