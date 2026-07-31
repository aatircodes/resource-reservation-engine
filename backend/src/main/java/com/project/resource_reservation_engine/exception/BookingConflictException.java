package com.project.resource_reservation_engine.exception;

public class BookingConflictException extends RuntimeException {

    private final ConflictReason reason;

    public BookingConflictException(ConflictReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ConflictReason getReason() {
        return reason;
    }
}