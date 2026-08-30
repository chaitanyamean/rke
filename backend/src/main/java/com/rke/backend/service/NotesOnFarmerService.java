package com.rke.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rke.backend.domain.NoteOnFarmer;
import com.rke.backend.dto.NotesOnFarmerRequest;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.NotesOnFarmerRepository;
import com.rke.backend.security.CurrentUserService;


@Service
public class NotesOnFarmerService {
    private final NotesOnFarmerRepository repository;
    private final CurrentUserService currentUserService;
    private final FarmerRepository farmerRepository;
    public NotesOnFarmerService(CurrentUserService currentUserService, NotesOnFarmerRepository repository, FarmerRepository farmerRepository) {
        this.currentUserService = currentUserService;
        this.repository = repository;
        this.farmerRepository = farmerRepository;
    }

    @Transactional
    public NoteOnFarmer create(NotesOnFarmerRequest request) {
            requireFarmer(request.farmerId());
        NoteOnFarmer noteOnFarmer = NoteOnFarmer.builder()
            .tenantId(currentUserService.getTenantId())
            .userId(currentUserService.getCurrentUserId())
            .farmerId(request.farmerId())
            .content(request.content())
            .build();
            noteOnFarmer = repository.save(noteOnFarmer);
        return noteOnFarmer;
    }

    private void requireFarmer(UUID farmerId) {
        if(!farmerRepository.existsById(farmerId)) {
            throw new IllegalArgumentException("Farmer Not Found" + farmerId);
        }

    }

    @Transactional(readOnly = true)
    public List<NoteOnFarmer> listByFarmer(UUID farmerId) {
        return repository.findByFarmerIdOrderByCreatedAtDesc(farmerId);
    }
    
}
