package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.CottonLotEntry;

public interface CottonLotEntryRepository extends JpaRepository<CottonLotEntry, UUID> {

    List<CottonLotEntry> findByCottonLotId(UUID cottonLotId);
}
