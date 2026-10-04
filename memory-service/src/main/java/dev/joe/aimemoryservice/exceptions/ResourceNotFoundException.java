package dev.joe.aimemoryservice.exceptions;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, long id) {
        super(resource + " with ID " + id + " was not found");
    }   
}