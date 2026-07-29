package com.project.resource_reservation_engine.exception;

public class ResourceFullyBookedException extends RuntimeException {
    public ResourceFullyBookedException(String message) {
        super(message);
    }
}