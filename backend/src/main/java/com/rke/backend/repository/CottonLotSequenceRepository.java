package com.rke.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.CottonLotSequence;

public interface CottonLotSequenceRepository extends JpaRepository<CottonLotSequence, UUID> {
}
