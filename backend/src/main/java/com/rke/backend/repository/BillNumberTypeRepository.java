package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.BillNumberType;

public interface BillNumberTypeRepository extends JpaRepository<BillNumberType, UUID> {

    List<BillNumberType> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<BillNumberType> findAllByOrderByNameAsc();

    List<BillNumberType> findByItemCategoryIdOrderByNameAsc(UUID itemCategoryId);
}
