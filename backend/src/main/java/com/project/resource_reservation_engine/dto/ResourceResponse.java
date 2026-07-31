package com.project.resource_reservation_engine.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceResponse {

     private Long id;
    private String name;
    private Integer capacity;
    private Integer availableSlots;
    private Instant createdAt;}