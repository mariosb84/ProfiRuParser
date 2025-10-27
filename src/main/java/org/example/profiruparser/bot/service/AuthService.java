package org.example.profiruparser.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.keyboards.MenuFactory;
import org.example.profiruparser.domain.dto.SignInRequest;
import org.example.profiruparser.domain.dto.SignUpRequest;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.errors.InvalidCredentialsException;
import org.example.profiruparser.service.AuthenticationService;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${app.trial.period-days}") /* ← ДОБАВЬ ЭТУ СТРОКУ*/
    private int trialPeriodDays;

    private final AuthenticationService authenticationService;
    private final UserServiceData userService;
    private final UserStateManager stateManager;
    private final TelegramService telegramService;
    private final MenuFactory menuFactory;

    private final SubscriptionService subscriptionService;

    public void handleLoginCommand(Long chatId) {
        stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_USERNAME);
        telegramService.sendMessage(chatId, "Введите логин:");
    }

    public void handleRegisterCommand(Long chatId) {

        /* ПРОВЕРЯЕМ - ЕСТЬ ЛИ УЖЕ ПОЛЬЗОВАТЕЛЬ С ЭТИМ CHAT_ID*/
        User existingUser = userService.findByTelegramChatId(chatId);
        if (existingUser != null) {
            telegramService.sendMessage(chatId,
                    "❌ Вы уже зарегистрированы!\n\n" +
                            "📧 Ваш логин: " + existingUser.getUsername() + "\n" +
                            "🆔 Ваш ID: " + existingUser.getId() + "\n\n" +
                            "Используйте /login для входа в существующий аккаунт.");
            return;
        }

        stateManager.setUserState(chatId, UserStateManager.STATE_REGISTER_USERNAME);
        telegramService.sendMessage(chatId, "Введите желаемый логин:");
    }

    public void handleUsernameInput(Long chatId, String username, boolean isRegistration) {
        if (username.length() < 3) {
            telegramService.sendMessage(chatId, "❌ Логин должен содержать минимум 3 символа:");
            return;
        }

        if (isRegistration && userService.findUserByUsername(username) != null) {
            telegramService.sendMessage(chatId, "❌ Пользователь уже существует. Введите другой логин:");
            return;
        }

        stateManager.setTempUsername(chatId, username);
        String nextState = isRegistration ? UserStateManager.STATE_REGISTER_PASSWORD : UserStateManager.STATE_WAITING_PASSWORD;
        stateManager.setUserState(chatId, nextState);
        telegramService.sendMessage(chatId, "Введите пароль:");
    }

    public void handlePasswordInput(Long chatId, String password, boolean isRegistration) {
        String username = stateManager.getTempUsername(chatId);
        stateManager.removeTempUsername(chatId);

        if (isRegistration) {
            handleRegistrationAndAutoLogin(chatId, username, password); /* ИЗМЕНЕНО*/
        } else {
            handleLogin(chatId, username, password);
        }
    }

    /* НОВЫЙ МЕТОД: регистрация + автоматическая авторизация*/
    private void handleRegistrationAndAutoLogin(Long chatId, String username, String password) {
        SignUpRequest request = new SignUpRequest();
        request.setUsername(username);
        request.setPassword(password);

        if (authenticationService.signUp(request).isPresent()) {

            /* АКТИВИРУЕМ ПРОБНЫЙ ПЕРИОД*/
            subscriptionService.activateTrialSubscription(username);

            SignInRequest loginRequest = new SignInRequest();
            loginRequest.setUsername(username);
            loginRequest.setPassword(password);

            Optional<User> user = authenticationService.signIn(loginRequest);
            if (user.isPresent()) {
                userService.updateTelegramChatId(username, chatId);
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                telegramService.sendMessage(chatId, "✅ Регистрация и авторизация успешны!");

                telegramService.sendMessage(chatId, "🎉 Вам активирован пробный период на " + trialPeriodDays + " дней!");

                /* АСИНХРОННО С ЗАДЕРЖКОЙ*/
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                        .execute(() -> {
                            telegramService.sendMessage(menuFactory.createMainMenu(chatId));
                        });
            } else {
                telegramService.sendMessage(chatId, "❌ Регистрация успешна, но авторизация не удалась. Используйте /login");
                stateManager.removeUserState(chatId);
            }
        } else {
            telegramService.sendMessage(chatId, "❌ Ошибка регистрации.");
            stateManager.removeUserState(chatId);
        }
    }

    private void handleLogin(Long chatId, String username, String password) {
        SignInRequest request = new SignInRequest();
        request.setUsername(username);
        request.setPassword(password);

        try {
            Optional<User> user = authenticationService.signIn(request);
            if (user.isPresent()) {
                userService.updateTelegramChatId(username, chatId);
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                telegramService.sendMessage(chatId, "✅ Авторизация успешна!");
                telegramService.sendMessage(menuFactory.createMainMenu(chatId));
            }
        } catch (InvalidCredentialsException e) {
            /* Ловим исключение "неверные креды"*/
            telegramService.sendMessage(chatId, "❌ Неверный логин или пароль. /login");
            stateManager.removeUserState(chatId);
        } catch (Exception e) {
            /* Все остальные ошибки*/
            log.error("Login error for user: {}", username, e);
            telegramService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте еще раз.");
            stateManager.removeUserState(chatId);
        }
    }

    public void handleLogout(Long chatId) {
        stateManager.clearUserData(chatId);
        telegramService.sendMessage(chatId, "👋 До свидания! Для возобновления работы нажмите : /start");
    }

}
