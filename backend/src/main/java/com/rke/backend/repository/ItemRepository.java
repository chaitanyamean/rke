package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.Item;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findAllByOrderByNameAsc();

    List<Item> findByItemCategoryIdOrderByNameAsc(UUID itemCategoryId);
}
