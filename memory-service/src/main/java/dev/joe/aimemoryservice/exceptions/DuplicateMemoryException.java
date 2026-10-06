package dev.joe.aimemoryservice.exceptions;

import dev.joe.aimemoryservice.dto.DuplicateMemoryCandidate;
import java.util.List;

public class DuplicateMemoryException extends RuntimeException {
    private final List<DuplicateMemoryCandidate> candidates;

    public DuplicateMemoryException(List<DuplicateMemoryCandidate> candidates) {
        super("Likely duplicate active memories already exist: "
            + candidates.stream().map(candidate -> Long.toString(candidate.id())).toList());
        this.candidates = List.copyOf(candidates);
    }

    public List<DuplicateMemoryCandidate> candidates() {
        return candidates;
    }
}
