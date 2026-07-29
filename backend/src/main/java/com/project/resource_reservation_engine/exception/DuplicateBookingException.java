package com.project.resource_reservation_engine.exception;

public class DuplicateBookingException extends RuntimeException {
    public DuplicateBookingException(String message) {
        super(message);
    }
}