package org.example.profiruparser.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.keyboards.MenuFactory;
import org.example.profiruparser.domain.dto.SignInRequest;
import org.example.profiruparser.domain.dto.SignUpRequest;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.AuthenticationService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationService authenticationService;
    private final UserServiceData userService;
    private final UserStateManager stateManager;
    private final TelegramService telegramService;
    private final MenuFactory menuFactory;

    public void handleLoginCommand(Long chatId) {
        stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_USERNAME);
        telegramService.sendMessage(chatId, "Введите логин:");
    }

    public void handleRegisterCommand(Long chatId) {
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
            handleRegistration(chatId, username, password);
        } else {
            handleLogin(chatId, username, password);
        }
    }

    private void handleRegistration(Long chatId, String username, String password) {
        SignUpRequest request = new SignUpRequest();
        request.setUsername(username);
        request.setPassword(password);

        if (authenticationService.signUp(request).isPresent()) {
            telegramService.sendMessage(chatId, "✅ Регистрация успешна! Теперь войдите: /login");
        } else {
            telegramService.sendMessage(chatId, "❌ Ошибка регистрации.");
        }
        stateManager.removeUserState(chatId);
    }

    private void handleLogin(Long chatId, String username, String password) {
        SignInRequest request = new SignInRequest();
        request.setUsername(username);
        request.setPassword(password);

        Optional<User> user = authenticationService.signIn(request);
        if (user.isPresent()) {
            userService.updateTelegramChatId(username, chatId);
            stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
            telegramService.sendMessage(chatId, "✅ Авторизация успешна!");
            telegramService.sendMessage(menuFactory.createMainMenu(chatId));
        } else {
            telegramService.sendMessage(chatId, "❌ Неверный логин или пароль. /login");
            stateManager.removeUserState(chatId);
        }
    }

    public void handleLogout(Long chatId) {
        stateManager.clearUserData(chatId);
        telegramService.sendMessage(chatId, "👋 До свидания! Для возобновления работы нажмите : /start");
    }

}
