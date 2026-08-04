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
    private final BookingConfirmationAttempt bookingConfirmationAttempt;

    private static final int MAX_RETRY_ATTEMPTS = 20;

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

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Booking confirmed = bookingConfirmationAttempt.attemptConfirm(resource.getId(), user, idempotencyKey);

                if (confirmed == null) {
                    Booking waitlisted = new Booking();
                    waitlisted.setUser(user);
                    waitlisted.setResource(resource);
                    waitlisted.setStatus(BookingStatus.WAITLISTED);
                    waitlisted.setIdempotencyKey(idempotencyKey);

                    Booking savedWaitlisted = bookingRepository.save(waitlisted);
                    return toResponse(savedWaitlisted);
                }

                return toResponse(confirmed);

            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    throw new BookingConflictException(ConflictReason.VERSION_CONFLICT,
                            "Resource was updated concurrently after " + MAX_RETRY_ATTEMPTS + " attempts, please retry");
                }
            }
        }

        // Unreachable — the loop above always returns or throws.
        throw new IllegalStateException("Booking retry loop exited without a result");
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
            retryOnConflict(() -> bookingConfirmationAttempt.attemptDecrement(resource.getId()));
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        if (wasConfirmed) {
            retryOnConflict(() -> bookingConfirmationAttempt.attemptPromotion(resource.getId()));
        }
    }

    private void retryOnConflict(Runnable attempt) {
        for (int i = 1; i <= MAX_RETRY_ATTEMPTS; i++) {
            try {
                attempt.run();
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (i == MAX_RETRY_ATTEMPTS) {
                    throw new BookingConflictException(ConflictReason.VERSION_CONFLICT,
                            "Resource was updated concurrently after " + MAX_RETRY_ATTEMPTS + " attempts, please retry");
                }
            }
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