package com.rke.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.CottonLot;

public interface CottonLotRepository extends JpaRepository<CottonLot, UUID> {

    List<CottonLot> findAllByOrderByLotDateDesc();

    List<CottonLot> findByLotDateBetweenOrderByLotDateDesc(LocalDate from, LocalDate to);

    List<CottonLot> findByLotDateGreaterThanEqualOrderByLotDateDesc(LocalDate from);

    List<CottonLot> findByLotDateLessThanEqualOrderByLotDateDesc(LocalDate to);
}
