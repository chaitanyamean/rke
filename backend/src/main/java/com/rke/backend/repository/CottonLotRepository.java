package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.CottonLot;

public interface CottonLotRepository extends JpaRepository<CottonLot, UUID> {

    List<CottonLot> findAllByOrderByLotDateDesc();
}
