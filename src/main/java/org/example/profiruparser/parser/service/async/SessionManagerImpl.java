package org.example.profiruparser.parser.service.async;

import org.openqa.selenium.Cookie;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/* 🔐 Менеджер сессий для асинхронной работы */
@Service
public class SessionManagerImpl implements SessionManager {

    /* Хранилище сессий: sessionId -> login */
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    /* Хранилище логинов: login -> sessionId */
    private final Map<String, String> loginToSession = new ConcurrentHashMap<>();
    /* 🔥 ИЗМЕНЕНИЕ: Хранилище cookies для сессий вместо браузеров */
    private final Map<String, Set<Cookie>> sessionCookies = new ConcurrentHashMap<>();

    @Override
    public String createSession(String login, String password) {
        /* Проверяем, есть ли уже активная сессия для этого пользователя */
        String existingSession = loginToSession.get(login);
        if (existingSession != null && activeSessions.containsKey(existingSession)) {
            System.out.println("🔄 Reusing existing session for user: " + login);
            return existingSession;
        }

        /* Создаем новую сессию */
        String sessionId = generateSessionId(login);
        activeSessions.put(sessionId, login);
        loginToSession.put(login, sessionId);

        System.out.println("✅ Created new session for user: " + login + " [Session: " + sessionId + "]");
        return sessionId;
    }

    /* 🔥 НОВЫЙ МЕТОД: Сохранить cookies сессии */
    public void saveSessionCookies(String sessionId, Set<Cookie> cookies) {
        sessionCookies.put(sessionId, cookies);
        System.out.println("🍪 Saved cookies for session: " + sessionId + " (" + cookies.size() + " cookies)");
    }

    /* 🔥 НОВЫЙ МЕТОД: Получить cookies сессии */
    public Set<Cookie> getSessionCookies(String sessionId) {
        Set<Cookie> cookies = sessionCookies.get(sessionId);
        System.out.println("🔍 Getting cookies for session " + sessionId + ": " + (cookies != null ? cookies.size() + " cookies" : "NO COOKIES"));
        return cookies;
    }

    @Override
    public Optional<String> getSessionUser(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    @Override
    public boolean isValidSession(String sessionId) {
        boolean isValid = activeSessions.containsKey(sessionId);
        System.out.println("🔍 Session validation: " + sessionId + " = " + isValid);
        return isValid;
    }

    @Override
    public void invalidateSession(String sessionId) {
        String login = activeSessions.get(sessionId);
        if (login != null) {
            /* 🔥 ИЗМЕНЕНИЕ: Удаляем только cookies, а не закрываем браузер */
            sessionCookies.remove(sessionId);
            activeSessions.remove(sessionId);
            loginToSession.remove(login);
            System.out.println("🗑️ Invalidated session and cookies for user: " + login);
        }
    }

    /* 🔧 Генератор ID сессии */
    private String generateSessionId(String login) {
        return "session_" + login + "_" + System.currentTimeMillis();
    }
}
