package com.project.resource_reservation_engine.repository;

import com.project.resource_reservation_engine.entity.Booking;
import com.project.resource_reservation_engine.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    long countByResourceIdAndStatus(Long resourceId, BookingStatus status);

    List<Booking> findByUserId(Long userId);

    boolean existsByUserIdAndResourceIdAndStatus(Long userId, Long resourceId, BookingStatus status);
}