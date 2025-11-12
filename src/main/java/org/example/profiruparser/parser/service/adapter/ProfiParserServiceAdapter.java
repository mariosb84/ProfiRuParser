package org.example.profiruparser.parser.service.adapter;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.parser.service.ProfiParserService;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;
import org.example.profiruparser.parser.service.async.AsyncProfiParserService;
import org.example.profiruparser.parser.service.async.SessionManager;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Primary /* ⚡ ВАЖНО: делаем этот бин основным!*/
@Service
public class ProfiParserServiceAdapter implements ProfiParserService {

    private final AsyncProfiParserService asyncService;
    private final SessionManager sessionManager;
    private String currentSessionId;

    @Autowired
    public ProfiParserServiceAdapter(AsyncProfiParserService asyncService,
                                     SessionManager sessionManager) {
        this.asyncService = asyncService;
        this.sessionManager = sessionManager;
    }

    @Override
    public List<ProfiOrder> parseOrders(String keyword) throws Exception {
        log.info("🎯 ADAPTER: parseOrders('{}') called", keyword);
        log.info("🎯 ADAPTER: Current session: {}", currentSessionId);

        if (currentSessionId == null || !sessionManager.isValidSession(currentSessionId)) {
            log.error("❌ ADAPTER: No valid session! Session: {}", currentSessionId);
            throw new IllegalStateException("Требуется авторизация. Session: " + currentSessionId);
        }

        log.info("✅ ADAPTER: Session valid, proceeding with search...");

        try {
            CompletableFuture<List<ProfiOrder>> future = asyncService.parseOrdersAsync(keyword, currentSessionId);

            /* 🔥 УБИРАЕМ ТАЙМАУТ ДЛЯ ТЕСТИРОВАНИЯ*/
            List<ProfiOrder> results = future.get(); /* Без таймаута*/

            log.info("✅ ADAPTER: Search completed. Found {} orders", results.size());
            return results;

        } catch (Exception e) {
            log.error("❌ ADAPTER: Search failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void ensureLoggedIn(String login, String password) throws LoginException {
        log.info("Adapter: ensuring login for user {}", login);

        try {
            /* 🔄 Используем новую асинхронную логику*/
            CompletableFuture<String> future = asyncService.createSessionAsync(login, password);
            log.info("⏳ ADAPTER: Waiting for session creation...");

           /* this.currentSessionId = future.get(30, TimeUnit.SECONDS);*/

            this.currentSessionId = future.get(2, TimeUnit.MINUTES); /* Увеличил до 2 минут*/

            log.info("Adapter: login successful, session: {}", currentSessionId);
        } catch (TimeoutException e) {
            log.error("⏰ ADAPTER: Login TIMEOUT");
            throw new LoginException("Таймаут логина: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ ADAPTER: Login FAILED - {}", e.getMessage(), e);
            throw new LoginException("Ошибка входа: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        log.info("Adapter: closing resources");
        if (currentSessionId != null) {
            sessionManager.invalidateSession(currentSessionId);
            currentSessionId = null;
        }
    }

    @Override
    public WebDriver getDriver() {
        log.warn("Adapter: getDriver() - метод устарел, используйте BrowserPool");
        return null;
    }
}
