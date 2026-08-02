package com.project.resource_reservation_engine.dto;

import com.project.resource_reservation_engine.entity.BookingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long resourceId;
    private String resourceName;
    private BookingStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer waitlistPosition;
}