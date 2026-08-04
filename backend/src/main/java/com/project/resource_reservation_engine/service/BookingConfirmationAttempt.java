package com.project.resource_reservation_engine.service;

import com.project.resource_reservation_engine.entity.Booking;
import com.project.resource_reservation_engine.entity.BookingStatus;
import com.project.resource_reservation_engine.entity.Resource;
import com.project.resource_reservation_engine.entity.User;
import com.project.resource_reservation_engine.exception.ResourceNotFoundException;
import com.project.resource_reservation_engine.repository.BookingRepository;
import com.project.resource_reservation_engine.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BookingConfirmationAttempt {

    private final ResourceRepository resourceRepository;
    private final BookingRepository bookingRepository;

    /**
     * Attempts to confirm one booking in a fresh transaction, so it reads
     * the latest committed resource state rather than a stale one carried
     * over from the caller's transaction.
     *
     * Returns null if the resource is genuinely full on this fresh read —
     * the caller is responsible for waitlisting in that case, not this method.
     *
     * Throws ObjectOptimisticLockingFailureException (unhandled here, left to
     * propagate) if this attempt loses a version race — the caller decides
     * whether to retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking attemptConfirm(Long resourceId, User user, String idempotencyKey) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        if (resource.getBookedCount() >= resource.getCapacity()) {
            return null;
        }

        resource.setBookedCount(resource.getBookedCount() + 1);
        resourceRepository.saveAndFlush(resource);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setResource(resource);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setIdempotencyKey(idempotencyKey);

        return bookingRepository.save(booking);
    }

    /**
     * Attempts to decrement bookedCount for a cancelled CONFIRMED booking,
     * in a fresh transaction so it reads the latest committed version.
     * Throws ObjectOptimisticLockingFailureException (left to propagate) if
     * this attempt loses a version race — the caller decides whether to retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptDecrement(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        resource.setBookedCount(resource.getBookedCount() - 1);
        resourceRepository.saveAndFlush(resource);
    }

    /**
     * Attempts to promote the oldest waitlisted booking for a resource,
     * in a fresh transaction so it reads the latest committed version
     * (in particular, the decrement from attemptDecrement, once committed).
     * Does nothing if no waitlisted booking exists.
     * Throws ObjectOptimisticLockingFailureException (left to propagate) if
     * this attempt loses a version race — the caller decides whether to retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptPromotion(Long resourceId) {
        bookingRepository.findFirstByResourceIdAndStatusOrderByCreatedAtAscIdAsc(resourceId, BookingStatus.WAITLISTED)
                .ifPresent(promoted -> {
                    Resource resource = resourceRepository.findById(resourceId)
                            .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
                    resource.setBookedCount(resource.getBookedCount() + 1);
                    resourceRepository.saveAndFlush(resource);
                    promoted.setStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(promoted);
                });
    }
}