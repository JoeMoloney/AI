package dev.joe.aimemoryservice.exceptions;

public class ProjectAlreadyExistsException extends RuntimeException {
    public ProjectAlreadyExistsException(String projectName) {
        super("A project named '"+projectName+"' already exists");
    }
}
