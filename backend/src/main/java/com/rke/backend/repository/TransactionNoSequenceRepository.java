package com.rke.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rke.backend.domain.TransactionNoSequence;

public interface TransactionNoSequenceRepository extends JpaRepository<TransactionNoSequence, UUID> {
}
