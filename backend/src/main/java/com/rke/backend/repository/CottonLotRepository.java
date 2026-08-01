package com.rke.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.CottonLot;

public interface CottonLotRepository extends JpaRepository<CottonLot, UUID> {

    List<CottonLot> findAllByOrderByLotDateAsc();

    List<CottonLot> findByLotDateBetweenOrderByLotDateAsc(LocalDate from, LocalDate to);

    List<CottonLot> findByLotDateGreaterThanEqualOrderByLotDateAsc(LocalDate from);

    List<CottonLot> findByLotDateLessThanEqualOrderByLotDateAsc(LocalDate to);
}
