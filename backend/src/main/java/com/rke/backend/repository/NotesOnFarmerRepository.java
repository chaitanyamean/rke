package com.rke.backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.rke.backend.domain.NoteOnFarmer;

public interface NotesOnFarmerRepository extends JpaRepository<NoteOnFarmer, UUID> {
        List<NoteOnFarmer> findByFarmerIdOrderByCreatedAtDesc(UUID farmerId);
}               // findAllByOrderByUsernameAsc
