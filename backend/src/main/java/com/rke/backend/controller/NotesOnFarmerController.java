package com.rke.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rke.backend.domain.NoteOnFarmer;
import com.rke.backend.dto.NotesOnFarmerRequest;
import com.rke.backend.service.NotesOnFarmerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notes-on-farmer")
public class NotesOnFarmerController {
    
    private final NotesOnFarmerService notesOnFarmerService;

    public NotesOnFarmerController(NotesOnFarmerService notesOnFarmerService) {
        this.notesOnFarmerService = notesOnFarmerService;
    }

    @GetMapping
    public List<NoteOnFarmer> get(@RequestParam UUID farmerId) {
        return notesOnFarmerService.listByFarmer(farmerId);
    }

    @PostMapping
    public NoteOnFarmer create(@Valid @RequestBody NotesOnFarmerRequest request) {
        return notesOnFarmerService.create(request);
    }



}
