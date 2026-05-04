package com.group7.backend.exception;

/**
 * Thrown when an upstream taxonomy provider (ESCO, Wikidata) is unreachable
 * or returns an error. Mapped to HTTP 503 by the global exception handler so
 * the autocomplete form can show a transient failure rather than hang or
 * leak a 5xx through.
 */
public class TaxonomyUpstreamException extends RuntimeException {

    public TaxonomyUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
