package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.ItemCategory;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {

    List<ItemCategory> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<ItemCategory> findAllByOrderByNameAsc();
}
