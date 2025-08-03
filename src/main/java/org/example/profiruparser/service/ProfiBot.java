package org.example.profiruparser.service;

import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.example.profiruparser.domain.dto.ProfiOrder;
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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@NoArgsConstructor
@AllArgsConstructor
public class ProfiBot extends TelegramLongPollingBot {

    @Value("${tg.username}")
    private String username;
    @Value("${tg.token}")
    private String token;

    // Добавляем логин и пароль для авторизации
    @Value("${profi.login}")
    private String profiLogin;
    @Value("${profi.password}")
    private String profiPassword;

    private final ProfiParser parser = new ProfiParser();
    private final ProfiResponder responder = new ProfiResponder();

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final Map<Long, String> tempPassportData = new HashMap<>();

    // Храним состояние авторизации и этапы ввода для каждого пользователя
    private enum BotState {
        NONE,
        WAITING_FOR_USERNAME,
        WAITING_FOR_PASSWORD,
        AUTHORIZED,
        WAITING_FOR_KEYWORD_1,
        WAITING_FOR_KEYWORD_2,
        WAITING_FOR_KEYWORD_3,
        WAITING_FOR_KEYWORD_4,
        WAITING_FOR_KEYWORD_5
    }

    // Для каждого chatId хранит состояние
    private final Map<Long, BotState> userStates = new HashMap<>();
    // Для временного хранения логина при вводе пароля
    private final Map<Long, String> tempLogins = new HashMap<>();

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
            // Логирование входящего update
            System.out.println("\n=== NEW UPDATE RECEIVED ===");
            System.out.println("Update ID: " + update.getUpdateId());
            System.out.println("From: " + (update.hasMessage() ? update.getMessage().getFrom().getUserName() : "callback"));

            if (update.hasCallbackQuery()) {
                handleCallbackUpdate(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            }
        } catch (Exception e) {
            System.err.println("ERROR in onUpdateReceived: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCallbackUpdate(Update update) {
        CallbackQuery callback = update.getCallbackQuery();
        Long chatId = callback.getMessage().getChatId();
        BotState state = userStates.getOrDefault(chatId, BotState.NONE);

        System.out.println("[CALLBACK] ChatID: " + chatId);
        System.out.println("Current state: " + state);
        System.out.println("Callback data: " + callback.getData());

        if (!isAuthorized(chatId)) {
            System.out.println("User not authorized, rejecting callback");
            sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
            answerCallback(callback, "Требуется авторизация");
            return;
        }

        handleCallback(callback);
    }

    private void handleTextMessage(Update update) {
        Message message = update.getMessage();
        String messageText = message.getText();
        Long chatId = message.getChatId();
        BotState state = userStates.getOrDefault(chatId, BotState.NONE);

        System.out.println("\n[MESSAGE] ChatID: " + chatId);
        System.out.println("Current state: " + state);
        System.out.println("Message text: " + messageText);
        System.out.println("User keywords: " + userKeyWords.getOrDefault(chatId, Collections.emptyList()));

        // Обработка команды /start
        if ("/start".equals(messageText)) {
            System.out.println("Handling /start command");
            handleStartCommand(chatId);
            return;
        }

        // Сначала проверяем ввод ключевых слов
        if (handleKeywordInput(chatId, state, messageText)) {
            return;
        }

        // Обработка неавторизованных пользователей
        if (!isAuthorized(chatId)) {
            System.out.println("Handling unauthorized user");
            handleUnauthorizedUser(chatId, state, messageText);
            return;
        }

        // Обработка команд меню
        if (state == BotState.AUTHORIZED) {
            System.out.println("Handling authorized command");
            handleAuthorizedCommands(chatId, messageText);
            return;
        }

        System.out.println("Unexpected state, requesting auth");
        sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
    }

    private void handleStartCommand(Long chatId) {
        userStates.put(chatId, BotState.NONE);
        tempLogins.remove(chatId);
        sendMessage(chatId, "Добро пожаловать! Для начала работы авторизуйтесь командой /login");
        System.out.println("Reset state to NONE for chat: " + chatId);
    }

    private void handleUnauthorizedUser(Long chatId, BotState state, String messageText) {
        switch (state) {
            case NONE:
                if ("/login".equals(messageText)) {
                    System.out.println("Starting login process");
                    userStates.put(chatId, BotState.WAITING_FOR_USERNAME);
                    sendMessage(chatId, "Введите логин:");
                } else {
                    sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
                }
                break;

            case WAITING_FOR_USERNAME:
                System.out.println("Received username: " + messageText);
                tempLogins.put(chatId, messageText.trim());
                userStates.put(chatId, BotState.WAITING_FOR_PASSWORD);
                sendMessage(chatId, "Введите пароль:");
                break;

            case WAITING_FOR_PASSWORD:
                String login = tempLogins.get(chatId);
                System.out.println("Received password for login: " + login);

                if (profiLogin.equals(login) && profiPassword.equals(messageText.trim())) {
                    System.out.println("Login successful");
                    userStates.put(chatId, BotState.AUTHORIZED);
                    tempLogins.remove(chatId);
                    sendMessage(chatId, "Авторизация прошла успешно!");
                    sendMainMenu(chatId);
                } else {
                    System.out.println("Invalid credentials");
                    sendMessage(chatId, "Неверный логин или пароль. Попробуйте снова.\nВведите логин:");
                    userStates.put(chatId, BotState.WAITING_FOR_USERNAME);
                }
                break;

            default:
                sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
                userStates.put(chatId, BotState.NONE);
                break;
        }
    }

    private boolean handleKeywordInput(Long chatId, BotState state, String messageText) {
        switch (state) {
            case WAITING_FOR_KEYWORD_1:
                System.out.println("Saving keyword 1: " + messageText);
                saveKeyword(chatId, 0, messageText);
                userStates.put(chatId, BotState.AUTHORIZED);
                sendKeywordsMenu(chatId);
                return true;

            case WAITING_FOR_KEYWORD_2:
                System.out.println("Saving keyword 2: " + messageText);
                saveKeyword(chatId, 1, messageText);
                userStates.put(chatId, BotState.AUTHORIZED);
                sendKeywordsMenu(chatId);
                return true;

            case WAITING_FOR_KEYWORD_3:
                System.out.println("Saving keyword 3: " + messageText);
                saveKeyword(chatId, 2, messageText);
                userStates.put(chatId, BotState.AUTHORIZED);
                sendKeywordsMenu(chatId);
                return true;

            case WAITING_FOR_KEYWORD_4:
                System.out.println("Saving keyword 4: " + messageText);
                saveKeyword(chatId, 3, messageText);
                userStates.put(chatId, BotState.AUTHORIZED);
                sendKeywordsMenu(chatId);
                return true;

            case WAITING_FOR_KEYWORD_5:
                System.out.println("Saving keyword 5: " + messageText);
                saveKeyword(chatId, 4, messageText);
                userStates.put(chatId, BotState.AUTHORIZED);
                sendKeywordsMenu(chatId);
                return true;
        }
        return false;
    }

    private void handleAuthorizedCommands(Long chatId, String messageText) {
        System.out.println("Processing command: " + messageText);

        switch (messageText) {
            case "/login":
                sendMessage(chatId, "Вы уже авторизованы.");
                sendMainMenu(chatId);
                break;

            case "Настройки ключевых слов":
                System.out.println("Showing keywords menu");
                sendKeywordsMenu(chatId);
                break;

            case "Добавить ключ 1":
                System.out.println("Preparing to receive keyword 1");
                userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_1);
                sendMessage(chatId, "Введите первое ключевое слово:");
                break;

            case "Добавить ключ 2":
                System.out.println("Preparing to receive keyword 2");
                userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_2);
                sendMessage(chatId, "Введите второе ключевое слово:");
                break;

            case "Добавить ключ 3":
                System.out.println("Preparing to receive keyword 3");
                userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_3);
                sendMessage(chatId, "Введите третье ключевое слово:");
                break;

            case "Добавить ключ 4":
                System.out.println("Preparing to receive keyword 4");
                userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_4);
                sendMessage(chatId, "Введите четвертое ключевое слово:");
                break;

            case "Добавить ключ 5":
                System.out.println("Preparing to receive keyword 5");
                userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_5);
                sendMessage(chatId, "Введите пятое ключевое слово:");
                break;

            case "Искать по всем ключам":
                System.out.println("Searching by all keywords: " +
                        userKeyWords.getOrDefault(chatId, Collections.emptyList()));
                searchByAllKeywords(chatId);
                break;

            default:
                System.out.println("Parsing message as search query");
                executor.submit(() -> {
                    try {
                        parser.login(profiLogin, profiPassword);
                        List<ProfiOrder> orders = parser.parseOrders(messageText);
                        if (orders.isEmpty()) {
                            sendMessage(chatId, "По вашему запросу ничего не найдено.");
                        } else {
                            orders.forEach(order -> sendOrderCard(chatId, order));
                        }
                    } catch (Exception e) {
                        System.err.println("Search error: " + e.getMessage());
                        sendMessage(chatId, "Произошла ошибка при поиске заказов.");
                    }
                });
                break;
        }
    }

    private boolean isAuthorized(Long chatId) {
        return userStates.getOrDefault(chatId, BotState.NONE) == BotState.AUTHORIZED;
    }

    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Главное меню:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Показать данные"));
        row1.add(new KeyboardButton("Настройки"));
        row1.add(new KeyboardButton("Настройки ключевых слов"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Выйти"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendOrderCard(Long chatId, ProfiOrder order) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(InlineKeyboardButton.builder()
                        .text("Откликнуться")
                        .callbackData("respond_" + order.getId())
                        .build())
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(formatOrder(order))
                .replyMarkup(markup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String formatOrder(ProfiOrder order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🆔 Заказ #").append(order.getId()).append("\n\n");
        sb.append("📌 Название: ").append(order.getTitle()).append("\n\n");
        sb.append("💰 Цена: ").append(order.getPrice()).append("\n\n");
        sb.append("📝 Описание:\n").append(order.getDescription());

        // Ограничим длину описания, если оно слишком длинное
        if (sb.length() > 4000) { // Максимальная длина сообщения в Telegram
            sb.setLength(4000 - 100); // Оставляем место для "..."
            sb.append("...\n\n(сообщение сокращено)");
        }

        return sb.toString();
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        if (data.startsWith("respond_")) {
            String orderId = data.substring("respond_".length());

            executor.submit(() -> {
                boolean success;
                try {
                    success = responder.respondToOrder(orderId, "Хочу выполнить этот заказ!");
                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                }

                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                answer.setText(success ? "Отклик отправлен!" : "Ошибка отправки");

                try {
                    execute(answer);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                sendMessage(chatId, success ? "Отклик успешно отправлен." : "Не удалось отправить отклик.");
            });
        }
    }

    // Дополнительный метод для ответа на callback без изменений
    private void answerCallback(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void shutdown() {
        parser.close();
        executor.shutdown();
    }

    private void sendKeywordsMenu(Long chatId) {
        List<String> keywords = userKeyWords.getOrDefault(chatId, new ArrayList<>());

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Текущие ключевые слова:\n" +
                String.join("\n", keywords) +
                "\n\nВыберите действие:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Кнопки для добавления ключевых слов
        for (int i = 1; i <= 5; i++) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Добавить ключ " + i));
            keyboard.add(row);
        }

        // Кнопка для поиска по всем ключам
        KeyboardRow searchRow = new KeyboardRow();
        searchRow.add(new KeyboardButton("Искать по всем ключам"));
        keyboard.add(searchRow);

        // Кнопка назад
        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("Назад"));
        keyboard.add(backRow);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveKeyword(Long chatId, int index, String keyword) {
        List<String> keywords = userKeyWords.getOrDefault(chatId, new ArrayList<>());

        // Убедимся, что список достаточно большой
        while (keywords.size() <= index) {
            keywords.add("");
        }

        keywords.set(index, keyword);
        userKeyWords.put(chatId, keywords);
        sendMessage(chatId, "Ключевое слово сохранено!");
    }

    private void searchByAllKeywords(Long chatId) {
        List<String> keywords = userKeyWords.getOrDefault(chatId, new ArrayList<>());
        if (keywords.isEmpty()) {
            sendMessage(chatId, "У вас нет сохраненных ключевых слов.");
            return;
        }

        executor.submit(() -> {
            try {
                parser.login(profiLogin, profiPassword);
                Set<ProfiOrder> allOrders = new LinkedHashSet<>(); // Для избежания дубликатов

                for (String keyword : keywords) {
                    if (!keyword.isEmpty()) {
                        List<ProfiOrder> orders = parser.parseOrders(keyword);
                        allOrders.addAll(orders);
                    }
                }

                if (allOrders.isEmpty()) {
                    sendMessage(chatId, "По вашим ключевым словам ничего не найдено.");
                } else {
                    allOrders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendMessage(chatId, "Произошла ошибка при поиске заказов.");
            }
        });
    }

}



