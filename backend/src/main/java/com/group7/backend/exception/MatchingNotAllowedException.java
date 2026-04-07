package com.group7.backend.exception;

public class MatchingNotAllowedException extends RuntimeException {
    public MatchingNotAllowedException(String message) {
        super(message);
    }
}
