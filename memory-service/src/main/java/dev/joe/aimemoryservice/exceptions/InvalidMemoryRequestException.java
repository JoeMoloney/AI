package dev.joe.aimemoryservice.exceptions;

public class InvalidMemoryRequestException extends RuntimeException {
    public InvalidMemoryRequestException(String message) {
        super(message);
    }
}
