package org.example.profiruparser.errors;

public class SessionExpiredException extends SearchException {
    public SessionExpiredException(String message) {
        super(message);
    }
}