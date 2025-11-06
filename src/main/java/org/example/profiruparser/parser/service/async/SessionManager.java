package org.example.profiruparser.parser.service.async;

import java.util.Optional;

public interface SessionManager {
    String createSession(String login, String password);
    Optional<String> getSessionUser(String sessionId);
    boolean isValidSession(String sessionId);
    void invalidateSession(String sessionId);
}