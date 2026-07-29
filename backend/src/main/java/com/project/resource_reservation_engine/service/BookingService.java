package com.project.resource_reservation_engine.service;

import com.project.resource_reservation_engine.dto.BookingResponse;
import com.project.resource_reservation_engine.dto.CreateBookingRequest;
import com.project.resource_reservation_engine.entity.Booking;
import com.project.resource_reservation_engine.entity.BookingStatus;
import com.project.resource_reservation_engine.entity.Resource;
import com.project.resource_reservation_engine.entity.User;
import com.project.resource_reservation_engine.exception.DuplicateBookingException;
import com.project.resource_reservation_engine.exception.ResourceFullyBookedException;
import com.project.resource_reservation_engine.exception.ResourceNotFoundException;
import com.project.resource_reservation_engine.repository.BookingRepository;
import com.project.resource_reservation_engine.repository.ResourceRepository;
import com.project.resource_reservation_engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    public BookingResponse createBooking(CreateBookingRequest request, String idempotencyKey) {
        Booking existing = bookingRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        User user = getCurrentUser();

        Resource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        boolean alreadyBooked = bookingRepository.existsByUserIdAndResourceIdAndStatus(
                user.getId(), resource.getId(), BookingStatus.CONFIRMED);
        if (alreadyBooked) {
            throw new DuplicateBookingException("You already have an active booking for this resource");
        }

        long confirmedCount = bookingRepository.countByResourceIdAndStatus(resource.getId(), BookingStatus.CONFIRMED);
        if (confirmedCount >= resource.getCapacity()) {
            throw new ResourceFullyBookedException("Resource is fully booked");
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setResource(resource);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setIdempotencyKey(idempotencyKey);

        Booking saved = bookingRepository.save(booking);
        return toResponse(saved);
    }

    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        User currentUser = getCurrentUser();
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
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
        return new BookingResponse(
                booking.getId(),
                booking.getResource().getId(),
                booking.getResource().getName(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}