package org.example.profiruparser.parser.service.async;

import org.example.profiruparser.domain.dto.ProfiOrder;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AsyncProfiParserService {
    CompletableFuture<List<ProfiOrder>> parseOrdersAsync(String keyword, String sessionId);
    CompletableFuture<String> createSessionAsync(String login, String password);
    CompletableFuture<Boolean> validateSessionAsync(String sessionId);
}