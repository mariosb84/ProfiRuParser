package org.example.profiruparser.parser.service.async;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.parser.service.impl.LoginService;
import org.example.profiruparser.parser.service.impl.OrderExtractionService;
import org.example.profiruparser.parser.service.impl.SearchService;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/* 🚀 Асинхронная реализация парсера с использованием пула браузеров */
@Slf4j
@Service
public class AsyncProfiParserServiceImpl implements AsyncProfiParserService {

    private final BrowserPool browserPool;
    private final SessionManager sessionManager;
    private final LoginService loginService;
    private final SearchService searchService;
    private final OrderExtractionService orderExtractionService;

    @Value("${webDriverManagerGetDriverSecond}")
    private String webDriverManagerGetDriverSecond;

    private final Map<String, Long> cookieCacheTime = new ConcurrentHashMap<>();                 /*меняем на "умные" задержки*/

    @Autowired
    public AsyncProfiParserServiceImpl(BrowserPool browserPool,
                                       SessionManager sessionManager,
                                       LoginService loginService,
                                       SearchService searchService,
                                       OrderExtractionService orderExtractionService) {
        this.browserPool = browserPool;
        this.sessionManager = sessionManager;
        this.loginService = loginService;
        this.searchService = searchService;
        this.orderExtractionService = orderExtractionService;
    }

    private boolean needsCookieReload(String sessionId) {                                  /*меняем на "умные" задержки*/
        Long lastLoad = cookieCacheTime.get(sessionId);
        return lastLoad == null || (System.currentTimeMillis() - lastLoad) > 300000; /* 5 минут*/
    }

    @Override
    public CompletableFuture<List<ProfiOrder>> parseOrdersAsync(String keyword, String sessionId) {
        log.info("🚀 Starting async search for: '{}' [Session: {}]", keyword, sessionId);

        return CompletableFuture.supplyAsync(() -> {
            WebDriver browser = null;
            try {
                /* 1. ПОЛУЧАЕМ БРАУЗЕР ИЗ ПУЛА */
                log.info("🔍 Acquiring browser from pool for search...");
                browser = browserPool.acquireBrowser().join();

                if (browser == null) {
                    throw new RuntimeException("Failed to acquire browser from pool");
                }

                /* 2. ЗАГРУЖАЕМ COOKIES СЕССИИ В БРАУЗЕР */
                log.info("🍪 Loading session cookies into browser...");
                loadSessionCookiesIntoBrowser(sessionId, browser);

                /* 3. ВЫПОЛНЯЕМ ПОИСК */
                log.info("🎯 Performing search for: '{}'", keyword);
                List<ProfiOrder> results = searchService.searchOrdersWithBrowser(keyword, orderExtractionService, browser);

                log.info("✅ Search completed. Found {} orders for: '{}'", results.size(), keyword);
                return results;

            } catch (Exception e) {
                log.error("❌ Async search failed for '{}': {}", keyword, e.getMessage(), e);
                throw new RuntimeException("Search failed: " + e.getMessage(), e);
            } finally {
                /* 🔥 ВСЕГДА ВОЗВРАЩАЕМ БРАУЗЕР В ПУЛ */
                if (browser != null) {
                    log.info("🔄 Returning browser to pool after search");
                    browserPool.releaseBrowser(browser);
                }
            }
        });
    }

    @Override
    public CompletableFuture<String> createSessionAsync(String login, String password) {
        log.info("🔐 Creating async session for user: {}", login);

        return CompletableFuture.supplyAsync(() -> {
            WebDriver browser = null;
            try {
                /* 1. ПОЛУЧАЕМ БРАУЗЕР ИЗ ПУЛА ДЛЯ АВТОРИЗАЦИИ */
                log.info("🔍 Acquiring browser for login...");
                browser = browserPool.acquireBrowser().join();

                if (browser == null) {
                    throw new RuntimeException("Failed to acquire browser from pool");
                }

                /* 2. ВЫПОЛНЯЕМ АВТОРИЗАЦИЮ */
                log.info("🎯 Performing login for user: {}", login);
                loginService.performLoginWithBrowser(login, password, browser);

                /* 3. СОХРАНЯЕМ COOKIES АВТОРИЗАЦИИ */
                log.info("🍪 Saving authentication cookies...");
                Set<Cookie> cookies = browser.manage().getCookies();

                /* 4. СОЗДАЕМ СЕССИЮ И СОХРАНЯЕМ COOKIES */
                String sessionId = sessionManager.createSession(login, password);
                ((SessionManagerImpl) sessionManager).saveSessionCookies(sessionId, cookies);

                log.info("✅ Login successful. Session created: {} with {} cookies", sessionId, cookies.size());
                return sessionId;

            } catch (Exception e) {
                log.error("❌ Async login failed for user '{}': {}", login, e.getMessage(), e);
                throw new RuntimeException("Login failed: " + e.getMessage(), e);
            } finally {
                /* 🔥 ВСЕГДА ВОЗВРАЩАЕМ БРАУЗЕР В ПУЛ */
                if (browser != null) {
                    log.info("🔄 Returning browser to pool after login");
                    browserPool.releaseBrowser(browser);
                }
            }
        });
    }

    /* 🔥 НОВЫЙ МЕТОД: Загрузка cookies сессии в браузер */
    private void loadSessionCookiesIntoBrowser(String sessionId, WebDriver browser) {
        try {
            Set<Cookie> cookies;

            /* 🔥 РАЗДЕЛЯЕМ ЛОГИКУ: берем cookies из кэша или перезагружаем */
            if (!needsCookieReload(sessionId)) {
                log.info("🍪 Using cached cookies for session: {}", sessionId);
                cookies = ((SessionManagerImpl) sessionManager).getSessionCookies(sessionId);
            } else {
                log.info("🍪 Reloading cookies for session: {}", sessionId);
                cookies = ((SessionManagerImpl) sessionManager).getSessionCookies(sessionId);
            }

            if (cookies != null && !cookies.isEmpty()) {
                log.info("🍪 Loading {} cookies into browser for session: {}", cookies.size(), sessionId);

                /* 🔥 ВАЖНО: Переходим на ТОЧНО ТУ ЖЕ СТРАНИЦУ где были получены cookies */
                /* browser.get("https://profi.ru/backoffice/n.php"); */ /* меняем на @Value */
                browser.get(this.webDriverManagerGetDriverSecond);
                Thread.sleep(3000); /* меняем на "умные" задержки */

                /* Удаляем все существующие cookies */
                browser.manage().deleteAllCookies();
                Thread.sleep(1000); /* меняем на "умные" задержки */

                /* Устанавливаем cookies сессии */
                for (Cookie cookie : cookies) {
                    try {
                        browser.manage().addCookie(cookie);
                        log.debug("✅ Added cookie: {} = {}", cookie.getName(), cookie.getValue());
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to add cookie: {}", cookie.getName());
                    }
                }

                /* 🔥 ВАЖНО: Обновляем страницу и ждем загрузки */
                browser.navigate().refresh();
                Thread.sleep(5000); /* меняем на "умные" задержки - Даем время на применение cookies */

                /* 🔥 ПРОВЕРЯЕМ АВТОРИЗАЦИЮ */
                String currentUrl = browser.getCurrentUrl();
                if (currentUrl.contains("n.php") || currentUrl.contains("backoffice")) {
                    log.info("✅ Cookies loaded successfully - user is authenticated");
                } else {
                    log.warn("⚠️ Possible authentication issue after loading cookies");
                }

            } else {
                log.warn("⚠️ No cookies found for session: {}", sessionId);
                throw new RuntimeException("No authentication cookies found for session");
            }

            /* 🔥 Всегда обновляем время кэша */
            cookieCacheTime.put(sessionId, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("❌ Failed to load cookies for session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Failed to load session cookies: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Boolean> validateSessionAsync(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isValid = sessionManager.isValidSession(sessionId);
            log.info("🔍 Session validation result: {} = {}", sessionId, isValid);
            return isValid;
        });
    }
}
