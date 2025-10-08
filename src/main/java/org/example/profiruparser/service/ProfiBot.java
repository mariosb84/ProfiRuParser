package org.example.profiruparser.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.domain.dto.SignInRequest;
import org.example.profiruparser.domain.dto.SignUpRequest;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.errors.InvalidCredentialsException;
import org.example.profiruparser.errors.LoginException;
import org.example.profiruparser.errors.SearchTimeoutException;
import org.example.profiruparser.errors.SessionExpiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class ProfiBot extends TelegramLongPollingBot {

    @Value("${tg.username}")
    private String username;

    @Value("${tg.token}")
    private String token;

    private final AuthenticationService authenticationService;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final ProfiParser parser = new ProfiParser();
    private final ProfiResponder responder = new ProfiResponder();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // Добавляем поле для хранения интервалов пользователей
    private final Map<Long, Integer> userIntervals = new HashMap<>(); // chatId -> интервал в минутах
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    private enum BotState {
        NONE,
        WAITING_FOR_USERNAME,
        WAITING_FOR_PASSWORD,
        AUTHORIZED_MAIN_MENU,
        AUTHORIZED_KEYWORDS_MENU,
        WAITING_FOR_KEYWORD_1,
        WAITING_FOR_KEYWORD_2,
        WAITING_FOR_KEYWORD_3,
        WAITING_FOR_KEYWORD_4,
        WAITING_FOR_KEYWORD_5,
        SEARCH_IN_PROGRESS,
        SUBSCRIPTION_MENU,
        REGISTER_USERNAME,
        REGISTER_PASSWORD,
        WAITING_FOR_INTERVAL,
        AUTO_SEARCH_SETTINGS
    }

    private final Map<Long, BotState> userStates = new HashMap<>();
    private final Map<Long, String> tempUsernames = new HashMap<>();
    private final Map<Long, List<String>> userKeyWords = new HashMap<>();

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            System.err.println("=== BOT ERROR ===");
            e.printStackTrace();
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "⚠️ Произошла ошибка. Попробуйте еще раз.");
            }
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        BotState state = userStates.getOrDefault(chatId, BotState.NONE);

        System.out.println("DEBUG handleTextMessage - ChatId: " + chatId + ", Text: " + text + ", State: " + state);

        // Обработка команд
        switch (text) {
            case "/start", "🏠 Старт":
                handleStartCommand(chatId);
                return;
            case "/register", "📝 Регистрация":
                handleRegisterCommand(chatId);
                return;
            case "/login", "🔑 Войти":
                userStates.put(chatId, BotState.WAITING_FOR_USERNAME);
                sendMessage(chatId, "Введите логин:");
                return;
        }

        //  ИЗМЕНИ ПРОВЕРКУ - обрабатываем кнопки через authorized command
        if (text.startsWith("✏️ Ключ ") && isAuthorized(chatId)) {
            handleAuthorizedCommand(chatId, text);
            return;
        }

        // Обработка состояний

        // СНАЧАЛА проверяем состояния ввода ключей
        if (handleKeywordState(chatId, state, text)) return;

        // Добавим обработку интервалов после обработки ключевых слов
        if (handleIntervalState(chatId, state, text)) return;

        // ПОТОМ обработка регистрации и логина
        if (handleRegistrationState(chatId, state, text)) return;
        if (handleLoginState(chatId, state, text)) return;

        // Обработка авторизованных команд
        if (isAuthorized(chatId)) {
            handleAuthorizedCommand(chatId, text);
        } else {
            sendMessage(chatId, "Пожалуйста, авторизуйтесь: /login");
        }
    }

    private void handleStartCommand(Long chatId) {
        userStates.put(chatId, BotState.NONE);
        tempUsernames.remove(chatId);
        userKeyWords.remove(chatId);

        if (isAuthorized(chatId)) {
            sendMainMenu(chatId);
        } else {
            sendWelcomeMenu(chatId);
        }

        // ВСЕГДА показываем стартовое меню, независимо от авторизации
        //sendWelcomeMenu(chatId);
    }

    private void sendWelcomeMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👋 Добро пожаловать!\n\nВыберите действие:");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📝 Регистрация"));
        row1.add(new KeyboardButton("🔑 Войти"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🏠 Старт"));

        rows.add(row1);
        rows.add(row2);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private boolean handleRegistrationState(Long chatId, BotState state, String text) {
        switch (state) {
            case REGISTER_USERNAME:
                if (text.length() < 3) {
                    sendMessage(chatId, "❌ Логин должен содержать минимум 3 символа:");
                    return true;
                }
                if (userService.findUserByUsername(text) != null) {
                    sendMessage(chatId, "❌ Пользователь уже существует. Введите другой логин:");
                    return true;
                }
                tempUsernames.put(chatId, text);
                userStates.put(chatId, BotState.REGISTER_PASSWORD);
                sendMessage(chatId, "Введите пароль:");
                return true;

            case REGISTER_PASSWORD:
                String username = tempUsernames.get(chatId);
                SignUpRequest request = new SignUpRequest();
                request.setUsername(username);
                request.setPassword(text);

                if (authenticationService.signUp(request).isPresent()) {
                    sendMessage(chatId, "✅ Регистрация успешна! Теперь войдите: /login");
                    userStates.put(chatId, BotState.NONE);
                } else {
                    sendMessage(chatId, "❌ Ошибка регистрации.");
                }
                return true;
        }
        return false;
    }

    private boolean handleLoginState(Long chatId, BotState state, String text) {
        switch (state) {
            case WAITING_FOR_USERNAME:
                tempUsernames.put(chatId, text);
                userStates.put(chatId, BotState.WAITING_FOR_PASSWORD);
                sendMessage(chatId, "Введите пароль:");
                return true;

            case WAITING_FOR_PASSWORD:
                String username = tempUsernames.get(chatId);
                SignInRequest request = new SignInRequest();
                request.setUsername(username);
                request.setPassword(text);

                try {
                    Optional<User> user = authenticationService.signIn(request);
                    if (user.isPresent()) {

                        // Сохраняем chatId в базе
                        userService.updateTelegramChatId(username, chatId);

                        userStates.put(chatId, BotState.AUTHORIZED_MAIN_MENU);
                        sendMessage(chatId, "✅ Авторизация успешна!");
                        sendMainMenu(chatId);
                    } else {
                        sendMessage(chatId, "❌ Неверный логин или пароль. /login");
                        userStates.put(chatId, BotState.NONE);
                    }
                } catch (InvalidCredentialsException e) {
                    sendMessage(chatId, "❌ Неверный логин или пароль. /login");
                    userStates.put(chatId, BotState.NONE);
                }
                return true;
        }
        return false;
    }

    private boolean handleKeywordState(Long chatId, BotState state, String text) {
        if (state.name().startsWith("WAITING_FOR_KEYWORD_")) {
            try {
                // 🔥 ПРОПУСТИ ВСЕ КОМАНДЫ из меню ключей
                Set<String> menuCommands = Set.of(
                        "✏️ Ключ 1", "✏️ Ключ 2", "✏️ Ключ 3", "✏️ Ключ 4", "✏️ Ключ 5",
                        "🧹 Очистить все",
                        "🔙 Назад",
                        "🚀 Поиск по ключам"
                );

                if (menuCommands.contains(text)) {
                    return false; // Пусть обрабатываются в handleAuthorizedCommand
                }

                String stateName = state.name();
                String keyNumStr = stateName.replace("WAITING_FOR_KEYWORD_", "");
                int keyNum = Integer.parseInt(keyNumStr);

                System.out.println("Saving keyword #" + keyNum + ": " + text);

                saveKeyword(chatId, keyNum - 1, text);
                sendMessage(chatId, "✅ Ключ " + keyNum + " сохранен!");
                sendKeywordsMenu(chatId);
                return true;
            } catch (Exception e) {
                System.err.println("Keyword save error: " + e.getMessage());
                sendMessage(chatId, "❌ Ошибка сохранения ключа");
                return true;
            }
        }
        return false;
    }

    private void handleAuthorizedCommand(Long chatId, String text) {
        // Получаем пользователя из базы по chatId вместо tempUsernames
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        // ✅ ПРОСТОЙ ВАРИАНТ - если не команда меню, то это поиск
        if (!isMenuCommand(text)) {
            handleSearch(chatId, text);
            return;
        }

        String username = user.getUsername();
        boolean hasSubscription = subscriptionService.isSubscriptionActive(username);

        // Блокировка функционала без подписки

        // Создай список разрешенных команд для пользователей без подписки
        List<String> allowedWithoutSubscription = Arrays.asList(
                "💳 Оплатить подписку",
                "1 месяц - 299₽",
                "12 месяцев - 2490₽",
                "🔙 Назад",
                "🏠 Старт",
                "📝 Регистрация",
                "🔑 Войти",
                "❌ Выйти"
        );

        if (!hasSubscription && !allowedWithoutSubscription.contains(text)) {
            sendMessage(chatId, "❌ Требуется активная подписка!");
            sendSubscriptionMenu(chatId);
            return;

        }

        switch (text) {
            case "🔍 Ручной поиск":
                sendMessage(chatId, "Введите поисковый запрос:");
                break;

            case "⚙️ Ключевые слова":
                userStates.put(chatId, BotState.AUTHORIZED_KEYWORDS_MENU);
                sendKeywordsMenu(chatId);
                break;

            case "🚀 Поиск по ключам":
                searchByKeywords(chatId);
                break;

            case "💳 Оплатить подписку":
                sendSubscriptionMenu(chatId);
                break;

            case "1 месяц - 299₽":
                activateSubscription(chatId, 30);
                break;

            case "12 месяцев - 2490₽":
                activateSubscription(chatId, 365);
                break;

            case "🧹 Очистить все": // ← ДОБАВЬ ЭТО
                clearAllKeywords(chatId);
                break;

            case "🔙 Назад":
                userStates.put(chatId, BotState.AUTHORIZED_MAIN_MENU);
                sendMainMenu(chatId);
                break;

            case "🔄 Обновить":
                sendMainMenu(chatId);
                break;

            case "⏰ Автопоиск":
                sendAutoSearchMenu(chatId);
                break;

            case "🔔 Включить автопоиск":
                userStates.put(chatId, BotState.WAITING_FOR_INTERVAL);
                sendMessage(chatId, "Введите интервал в минутах (например, 60 для 1 часа):");
                break;

            case "🔕 Выключить автопоиск":
                stopAutoSearch(chatId);
                sendMessage(chatId, "✅ Автопоиск отключен");
                sendAutoSearchMenu(chatId);
                break;

            case "❌ Выйти":
                userStates.put(chatId, BotState.NONE);
                tempUsernames.remove(chatId);
                sendMessage(chatId, "👋 До свидания! для возобновления работы нажмите : /start");
                break;

            case "30 мин":
            case "60 мин":
            case "120 мин":
                int interval = Integer.parseInt(text.replace(" мин", ""));
                userIntervals.put(chatId, interval);
                startAutoSearch(chatId, interval);
                sendMessage(chatId, "✅ Автопоиск включен! Проверка каждые " + interval + " минут");
                sendAutoSearchMenu(chatId);
                break;

            default:
                if (text.startsWith("✏️ Ключ ")) {
                    handleEditKeyword(chatId, text);
                } else if (userStates.get(chatId) == BotState.AUTHORIZED_MAIN_MENU) {
                    handleSearch(chatId, text);
                } else {
                    sendMessage(chatId, "Неизвестная команда");
                }
        }
    }

    private void handleEditKeyword(Long chatId, String text) {
        try {
            System.out.println("TEXT: '" + text + "'");
            int keyNum = Integer.parseInt(text.replace("✏️ Ключ ", ""));
            System.out.println("Setting state: WAITING_FOR_KEYWORD_" + keyNum);

            if (keyNum >= 1 && keyNum <= 5) {
                userStates.put(chatId, BotState.valueOf("WAITING_FOR_KEYWORD_" + keyNum));
                sendMessage(chatId, "Введите новое значение для ключа " + keyNum + ":");
            }
        } catch (Exception e) {
            System.err.println("Edit keyword error: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка");
        }
    }

    private void activateSubscription(Long chatId, int days) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        if (subscriptionService.activateSubscription(user.getUsername(), days)) {
            LocalDateTime endDate = subscriptionService.getSubscriptionEndDate(user.getUsername());
            sendMessage(chatId, "✅ Подписка активирована до: " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            sendMainMenu(chatId);
        } else {
            sendMessage(chatId, "❌ Ошибка активации подписки");
        }
    }

    private void sendMainMenu(Long chatId) {

        User user = userService.findByTelegramChatId(chatId);
        String status = user != null ? getSubscriptionStatus(user.getUsername()) : "❌ Подписка: не активна";

        /*String username = tempUsernames.get(chatId);
        String status = getSubscriptionStatus(username);*/

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🏠 *Главное меню*\n\n" + status + "\n\nВыберите действие:");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🔍 Ручной поиск"));
        row1.add(new KeyboardButton("⚙️ Ключевые слова"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🚀 Поиск по ключам"));
        row2.add(new KeyboardButton("💳 Оплатить подписку"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("⏰ Автопоиск"));
        row3.add(new KeyboardButton("🔄 Обновить"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("❌ Выйти"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private String getSubscriptionStatus(String username) {
        if (username == null) return "❌ Подписка: не активна";

        LocalDateTime endDate = subscriptionService.getSubscriptionEndDate(username);
        if (endDate == null) return "❌ Подписка: не активна";

        if (endDate.isAfter(LocalDateTime.now())) {
            return "✅ Подписка активна до: " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        } else {
            return "❌ Подписка истекла: " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        }
    }

    private void sendSubscriptionMenu(Long chatId) {
        userStates.put(chatId, BotState.SUBSCRIPTION_MENU);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💳 *Выбор подписки*\n\n✅ Неограниченный поиск\n✅ Автопоиск по ключам\n✅ Быстрые отклики");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("1 месяц - 299₽"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("12 месяцев - 2490₽"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private void sendKeywordsMenu(Long chatId) {
        List<String> keywords = userKeyWords.getOrDefault(chatId, Arrays.asList("", "", "", "", ""));

        StringBuilder text = new StringBuilder("⚙️ *Ключевые слова:*\n");
        for (int i = 0; i < 5; i++) {
            text.append(i + 1).append(". ").append(keywords.get(i).isEmpty() ? "не задан" : keywords.get(i)).append("\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text.toString());
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("✏️ Ключ 1"));
        row1.add(new KeyboardButton("✏️ Ключ 2"));
        row1.add(new KeyboardButton("✏️ Ключ 3"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("✏️ Ключ 4"));
        row2.add(new KeyboardButton("✏️ Ключ 5"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🚀 Поиск по ключам"));
        row3.add(new KeyboardButton("🧹 Очистить все"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private void handleSearch(Long chatId, String query) {
        // Получаем пользователя из базы вместо tempUsernames
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        if (!subscriptionService.isSubscriptionActive(user.getUsername())) {
            sendMessage(chatId, "❌ Требуется активная подписка!");
            return;
        }

        executor.submit(() -> {
            try {
                sendMessage(chatId, "🔍 Идет поиск...");

                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                List<ProfiOrder> orders = parser.parseOrders(query);

                if (orders.isEmpty()) {
                    sendMessage(chatId, "❌ Ничего не найдено");
                } else {
                    sendMessage(chatId, "✅ Найдено: " + orders.size() + " заказов");
                    orders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
            }
        });
    }

    private void searchByKeywords(Long chatId) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        // Используем user.getUsername() вместо переменной username
        if (!subscriptionService.isSubscriptionActive(user.getUsername())) {
            sendMessage(chatId, "❌ Требуется активная подписка!");
            return;
        }

        List<String> keywords = userKeyWords.getOrDefault(chatId, new ArrayList<>())
                .stream().filter(k -> k != null && !k.trim().isEmpty()).toList();

        if (keywords.isEmpty()) {
            sendMessage(chatId, "❌ Нет ключевых слов");
            return;
        }

        executor.submit(() -> {
            try {
                // Убираем повторное получение пользователя - он уже есть
                sendMessage(chatId, "🚀 Идет поиск по " + keywords.size() + " ключам...");

                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                Set<ProfiOrder> allOrders = new LinkedHashSet<>();

                for (String keyword : keywords) {
                    allOrders.addAll(parser.parseOrders(keyword));
                    Thread.sleep(1000);
                }

                if (allOrders.isEmpty()) {
                    sendMessage(chatId, "❌ По ключам ничего не найдено");
                } else {
                    sendMessage(chatId, "✅ Найдено: " + allOrders.size() + " заказов");
                    allOrders.forEach(order -> sendOrderCard(chatId, order));
                }

            } catch (SessionExpiredException e) {
                sendMessage(chatId, "🔐 *Сессия истекла*\n\nПожалуйста, войдите заново через меню");
                // Можно предложить перелогин
            } catch (SearchTimeoutException e) {
                sendMessage(chatId, "⏱️ *Таймаут поиска*\n\nСайт отвечает медленно. Попробуйте через 5-10 минут");
            } catch (LoginException e) {
                sendMessage(chatId, "❌ *Ошибка входа*\n\nПроверьте логин/пароль и попробуйте снова" + e.getMessage());
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ *Временная ошибка*\n\nСервис временно недоступен. Попробуйте позже");
            }
        });
    }

    private void sendOrderCard(Long chatId, ProfiOrder order) {
        String text = String.format(
                "🆔 Заказ #%s\n📌 %s\n💰 %s\n📅 %s\n📝 %s",
                order.getId(), order.getTitle(), order.getPrice(),order.getCreationTime(),
                order.getDescription().length() > 1000 ?
                        order.getDescription().substring(0, 1000) + "..." : order.getDescription()
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(InlineKeyboardButton.builder()
                        .text("Откликнуться")
                        .callbackData("respond_" + order.getId())
                        .build())
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(markup)
                .build();

        executeMessage(message);
    }

    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();

        if (data.startsWith("respond_")) {
            String orderId = data.substring("respond_".length());
            executor.submit(() -> {
                try {
                    boolean success = responder.respondToOrder(orderId, "Хочу выполнить заказ!");
                    answerCallback(callback, success ? "✅ Отклик отправлен" : "❌ Ошибка отправки");
                } catch (Exception e) {
                    answerCallback(callback, "❌ Ошибка");
                }
            });
        }
    }

    private void answerCallback(CallbackQuery callback, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callback.getId());
        answer.setText(text);
        executeMessage(answer);
    }

    private void saveKeyword(Long chatId, int index, String keyword) {
        // Создаем список из 5 пустых строк если его нет
        List<String> keywords = userKeyWords.getOrDefault(chatId,
                new ArrayList<>(Arrays.asList("", "", "", "", "")));

        // Обновляем нужный ключ
        if (index >= 0 && index < keywords.size()) {
            keywords.set(index, keyword);
            userKeyWords.put(chatId, keywords);
            System.out.println("Saved keyword " + (index + 1) + ": " + keyword);
        }
    }

    private boolean isAuthorized(Long chatId) {
        BotState state = userStates.getOrDefault(chatId, BotState.NONE);

        // Добавляем логирование
        System.out.println("DEBUG isAuthorized - ChatId: " + chatId + ", State: " + state);

        User user = userService.findByTelegramChatId(chatId);
        boolean hasUserInDb = user != null;

        System.out.println("DEBUG isAuthorized - User in DB: " + hasUserInDb);
        if (user != null) {
            System.out.println("DEBUG isAuthorized - Username: " + user.getUsername());
        }

        boolean isAuth = (state == BotState.AUTHORIZED_MAIN_MENU ||
                state == BotState.AUTHORIZED_KEYWORDS_MENU ||
                state.name().startsWith("WAITING_FOR_KEYWORD") ||
                state == BotState.SEARCH_IN_PROGRESS ||
                state == BotState.SUBSCRIPTION_MENU ||
                state == BotState.AUTO_SEARCH_SETTINGS || // ← ДОБАВЬТЕ ЭТО!
                state == BotState.WAITING_FOR_INTERVAL) && hasUserInDb; // ← И ЭТО!

        System.out.println("DEBUG isAuthorized - Result: " + isAuth);
        return isAuth;
    }

    private void handleRegisterCommand(Long chatId) {
        userStates.put(chatId, BotState.REGISTER_USERNAME);
        sendMessage(chatId, "Введите желаемый логин:");
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        executeMessage(message);
    }

    private void executeMessage(BotApiMethod<?> message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        parser.close();
        executor.shutdown();
        scheduler.shutdown();

        // Отменяем все scheduled tasks
        scheduledTasks.values().forEach(future -> future.cancel(false));
    }

    private void clearAllKeywords(Long chatId) {
        userKeyWords.put(chatId, Arrays.asList("", "", "", "", ""));
        sendMessage(chatId, "✅ Все ключевые слова очищены!");
        sendKeywordsMenu(chatId);
    }

    private void sendAutoSearchMenu(Long chatId) {
        userStates.put(chatId, BotState.AUTO_SEARCH_SETTINGS);

        // Проверяем, есть ли активная задача автопоиска
        boolean isAutoSearchRunning = scheduledTasks.containsKey(chatId);
        Integer currentInterval = userIntervals.get(chatId);

        String status;
        if (isAutoSearchRunning && currentInterval != null) {
            status = "✅ Включен (каждые " + currentInterval + " мин.)";
        } else {
            status = "❌ Выключен";
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⏰ *Настройки автопоиска*\n\nТекущий статус: " + status);
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        if (isAutoSearchRunning) {
            row1.add(new KeyboardButton("🔕 Выключить автопоиск"));
        } else {
            row1.add(new KeyboardButton("🔔 Включить автопоиск"));
        }

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("30 мин"));
        row2.add(new KeyboardButton("60 мин"));
        row2.add(new KeyboardButton("120 мин"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private boolean handleIntervalState(Long chatId, BotState state, String text) {
        if (state == BotState.WAITING_FOR_INTERVAL) {
            try {
                int interval = Integer.parseInt(text);
                if (interval < 5) {
                    sendMessage(chatId, "❌ Минимальный интервал - 5 минут");
                    return true;
                }
                if (interval > 1440) {
                    sendMessage(chatId, "❌ Максимальный интервал - 24 часа (1440 минут)");
                    return true;
                }

                userIntervals.put(chatId, interval);
                startAutoSearch(chatId, interval);
                // Убираем sendMessage отсюда - он уже есть в startAutoSearch
                sendAutoSearchMenu(chatId); // Обновляем меню
                return true;

            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Введите число (интервал в минутах):");
                return true;
            }
        }
        return false;
    }

    private void startAutoSearch(Long chatId, int intervalMinutes) {
        System.out.println("DEBUG startAutoSearch - Starting for chatId: " + chatId + ", interval: " + intervalMinutes);
        stopAutoSearch(chatId);

        // Получаем пользователя из базы по chatId
        User user = userService.findByTelegramChatId(chatId);
        if (user == null || !subscriptionService.isSubscriptionActive(user.getUsername())) {
            sendMessage(chatId, "❌ Автопоиск остановлен - требуется активная подписка");
            return;
        }

        final String username = user.getUsername();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!subscriptionService.isSubscriptionActive(username)) {
                    sendMessage(chatId, "❌ Автопоиск остановлен - подписка истекла");
                    stopAutoSearch(chatId);
                    return;
                }

                searchByKeywords(chatId);

            } catch (Exception e) {
                System.err.println("Ошибка в автопоиске: " + e.getMessage());
            }
        }, 0, intervalMinutes, TimeUnit.MINUTES);

        scheduledTasks.put(chatId, future);
        userIntervals.put(chatId, intervalMinutes); // ← Сохраняем интервал!

        System.out.println("DEBUG startAutoSearch - Scheduled task added for chatId: " + chatId);
        System.out.println("DEBUG startAutoSearch - Scheduled tasks size: " + scheduledTasks.size());

        sendMessage(chatId, "✅ Автопоиск включен! Проверка каждые " + intervalMinutes + " минут");
    }

    private void stopAutoSearch(Long chatId) {
        ScheduledFuture<?> future = scheduledTasks.get(chatId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(chatId);
        }
        userIntervals.remove(chatId);
    }

    private boolean isMenuCommand(String text) {
        return text.equals("🔍 Ручной поиск") ||
                text.equals("⚙️ Ключевые слова") ||
                text.equals("🚀 Поиск по ключам") ||
                text.equals("💳 Оплатить подписку") ||
                text.equals("1 месяц - 299₽") ||
                text.equals("12 месяцев - 2490₽") ||
                text.equals("🧹 Очистить все") ||
                text.equals("🔙 Назад") ||
                text.equals("🔄 Обновить") ||
                text.equals("⏰ Автопоиск") ||
                text.equals("🔔 Включить автопоиск") ||
                text.equals("🔕 Выключить автопоиск") ||
                text.equals("❌ Выйти") ||
                text.equals("30 мин") ||
                text.equals("60 мин") ||
                text.equals("120 мин") ||
                text.startsWith("✏️ Ключ ");
    }

}

