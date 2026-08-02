package com.project.resource_reservation_engine.service;

import com.project.resource_reservation_engine.dto.BookingResponse;
import com.project.resource_reservation_engine.dto.CreateBookingRequest;
import com.project.resource_reservation_engine.entity.Booking;
import com.project.resource_reservation_engine.entity.BookingStatus;
import com.project.resource_reservation_engine.entity.Resource;
import com.project.resource_reservation_engine.entity.User;
import com.project.resource_reservation_engine.exception.BookingConflictException;
import com.project.resource_reservation_engine.exception.ConflictReason;
import com.project.resource_reservation_engine.exception.DuplicateBookingException;
import com.project.resource_reservation_engine.exception.ResourceNotFoundException;
import com.project.resource_reservation_engine.repository.BookingRepository;
import com.project.resource_reservation_engine.repository.ResourceRepository;
import com.project.resource_reservation_engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, String idempotencyKey) {
        Booking existing = bookingRepository.findByIdempotencyKey(idempotencyKey).orElse(null);

        if (existing != null) {
            return toResponse(existing);
        }

        User user = getCurrentUser();

        Resource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        boolean alreadyConfirmed = bookingRepository.existsByUserIdAndResourceIdAndStatus(
                user.getId(), resource.getId(), BookingStatus.CONFIRMED);
        boolean alreadyWaitlisted = bookingRepository.existsByUserIdAndResourceIdAndStatus(
                user.getId(), resource.getId(), BookingStatus.WAITLISTED);

        if (alreadyConfirmed || alreadyWaitlisted) {
            throw new DuplicateBookingException("You already have an active booking or waitlist entry for this resource");
        }

        if (resource.getBookedCount() >= resource.getCapacity()) {
            Booking waitlisted = new Booking();
            waitlisted.setUser(user);
            waitlisted.setResource(resource);
            waitlisted.setStatus(BookingStatus.WAITLISTED);
            waitlisted.setIdempotencyKey(idempotencyKey);

            Booking savedWaitlisted = bookingRepository.save(waitlisted);
            return toResponse(savedWaitlisted);
        }

        resource.setBookedCount(resource.getBookedCount() + 1);
        try {
            resourceRepository.saveAndFlush(resource);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new BookingConflictException(ConflictReason.VERSION_CONFLICT,
                    "Resource was updated concurrently, please retry");
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setResource(resource);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setIdempotencyKey(idempotencyKey);

        Booking saved = bookingRepository.save(booking);
        return toResponse(saved);
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        User currentUser = getCurrentUser();
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        Resource resource = booking.getResource();
        boolean wasConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;

        if (wasConfirmed) {
            resource.setBookedCount(resource.getBookedCount() - 1);
            try {
                resourceRepository.saveAndFlush(resource);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new BookingConflictException(ConflictReason.VERSION_CONFLICT,
                        "Resource was updated concurrently, please retry");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

       if (wasConfirmed) {
            bookingRepository.findFirstByResourceIdAndStatusOrderByCreatedAtAscIdAsc(resource.getId(), BookingStatus.WAITLISTED)
                    .ifPresent(promoted -> {
                        resource.setBookedCount(resource.getBookedCount() + 1);
                        resourceRepository.saveAndFlush(resource);
                        promoted.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(promoted);
                    });
        }
    }

    public List<BookingResponse> getBookingsForUser() {
        User currentUser = getCurrentUser();
        return bookingRepository.findByUserId(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private BookingResponse toResponse(Booking booking) {
       Integer waitlistPosition = null;
        if (booking.getStatus() == BookingStatus.WAITLISTED) {
            long ahead = bookingRepository.countAheadInWaitlist(
                    booking.getResource().getId(), BookingStatus.WAITLISTED, booking.getCreatedAt(), booking.getId());
            waitlistPosition = (int) ahead + 1;
        }

        return new BookingResponse(
                booking.getId(),
                booking.getResource().getId(),
                booking.getResource().getName(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                waitlistPosition
        );
    }
}