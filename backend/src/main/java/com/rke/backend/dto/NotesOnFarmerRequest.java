package com.rke.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotesOnFarmerRequest(

    @NotBlank(message = "content is mandatory")
    String content,

    @NotNull(message = "Farmer ID is mandatory")
    UUID farmerId  
    
){}
