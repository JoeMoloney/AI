package dev.joe.aimemoryservice.exceptions;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
public class GlobalExceptionHandler {
    @ExceptionHandler(ProjectAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleProjectAlreadyExists(ProjectAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            exception.getMessage()
        );
        problem.setTitle("Project already exists");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            detail
        );
        problem.setTitle("Request validation failed");

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            exception.getMessage()
        );
        problem.setTitle("Resource not found");

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(problem);
    }

    @ExceptionHandler(InvalidMemoryRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMemoryRequest(InvalidMemoryRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            exception.getMessage()
        );
        problem.setTitle("Invalid memory request");

        return ResponseEntity
            .badRequest()
            .body(problem);
    }
}
