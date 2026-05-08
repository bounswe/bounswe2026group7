package com.group7.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MilestoneConflictException extends RuntimeException {
    public MilestoneConflictException(String message) {
        super(message);
    }
}
