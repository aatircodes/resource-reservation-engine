package com.project.resource_reservation_engine.repository;

import com.project.resource_reservation_engine.entity.Booking;
import com.project.resource_reservation_engine.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    long countByResourceIdAndStatus(Long resourceId, BookingStatus status);

    List<Booking> findByUserId(Long userId);

    boolean existsByUserIdAndResourceIdAndStatus(Long userId, Long resourceId, BookingStatus status);

    Optional<Booking> findFirstByResourceIdAndStatusOrderByCreatedAtAscIdAsc(Long resourceId, BookingStatus status);

 @Query("SELECT COUNT(b) FROM Booking b WHERE b.resource.id = :resourceId AND b.status = :status " +
           "AND (b.createdAt < :createdAt OR (b.createdAt = :createdAt AND b.id < :id))")
   long countAheadInWaitlist(@Param("resourceId") Long resourceId, @Param("status") BookingStatus status,
                               @Param("createdAt") Instant createdAt, @Param("id") Long id);

}