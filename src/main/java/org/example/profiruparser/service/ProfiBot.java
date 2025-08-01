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
        if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            if (!isAuthorized(chatId)) {
                sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
                answerCallback(update.getCallbackQuery(), "Требуется авторизация");
                return;
            }
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            BotState state = userStates.getOrDefault(chatId, BotState.NONE);

            if ("/start".equals(messageText)) {
                sendMessage(chatId, "Добро пожаловать! Для начала работы авторизуйтесь командой /login");
                userStates.put(chatId, BotState.NONE);
                tempLogins.remove(chatId);
                return;
            }

            if (!isAuthorized(chatId)) {
                // Если пользователь не авторизован, обрабатываем команды авторизации
                switch (state) {
                    case NONE:
                        if ("/login".equals(messageText)) {
                            sendMessage(chatId, "Введите логин:");
                            userStates.put(chatId, BotState.WAITING_FOR_USERNAME);
                        } else {
                            sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
                        }
                        break;
                    case WAITING_FOR_USERNAME:
                        tempLogins.put(chatId, messageText.trim());
                        sendMessage(chatId, "Введите пароль:");
                        userStates.put(chatId, BotState.WAITING_FOR_PASSWORD);
                        break;
                    case WAITING_FOR_PASSWORD:
                        String login = tempLogins.get(chatId);
                        String password = messageText.trim();

                        if (profiLogin.equals(login) && profiPassword.equals(password)) {
                            userStates.put(chatId, BotState.AUTHORIZED);
                            tempLogins.remove(chatId);
                            sendMessage(chatId, "Авторизация прошла успешно!");
                            sendMainMenu(chatId);

                        } else {
                            sendMessage(chatId, "Неверный логин или пароль. Попробуйте снова.\nВведите логин:");
                            userStates.put(chatId, BotState.WAITING_FOR_USERNAME);
                        }
                        break;
                    default:
                        sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
                        userStates.put(chatId, BotState.NONE);
                        break;
                }
                return;
            }
            // Если авторизован, обрабатываем команды меню и остальной функционал
            if (state == BotState.AUTHORIZED) {
                switch (messageText) {
                    case "/login":
                        sendMessage(chatId, "Вы уже авторизованы.");
                        sendMainMenu(chatId);
                        break;
                    case "Показать данные":
                        sendMessage(chatId, "Здесь будет логика показа данных...");
                        break;
                    case "Настройки":
                        sendMessage(chatId, "Здесь будет логика настроек...");
                        break;

                    case "Настройки ключевых слов":
                        sendKeywordsMenu(chatId);
                        break;
                    case "Назад":
                        sendMainMenu(chatId);
                        break;
                    case "Искать по всем ключам":
                        searchByAllKeywords(chatId);
                        break;
                    case "Добавить ключ 1":
                        sendMessage(chatId, "Введите первое ключевое слово:");
                        userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_1);
                        break;
                    case "Добавить ключ 2":
                        sendMessage(chatId, "Введите второе ключевое слово:");
                        userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_2);
                        break;
                    case "Добавить ключ 3":
                        sendMessage(chatId, "Введите третье ключевое слово:");
                        userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_3);
                        break;
                    case "Добавить ключ 4":
                        sendMessage(chatId, "Введите четвертое ключевое слово:");
                        userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_4);
                        break;
                    case "Добавить ключ 5":
                        sendMessage(chatId, "Введите пятое ключевое слово:");
                        userStates.put(chatId, BotState.WAITING_FOR_KEYWORD_5);
                        break;

                    case "Выйти":
                        userStates.put(chatId, BotState.NONE);
                        sendMessage(chatId, "Вы вышли из системы. Для повторной авторизации используйте /login");
                        break;

                    default:

                        // Сначала проверяем, не вводится ли ключевое слово
                        if (state == BotState.WAITING_FOR_KEYWORD_1) {
                            saveKeyword(chatId, 0, messageText);
                            userStates.put(chatId, BotState.AUTHORIZED);
                            sendKeywordsMenu(chatId);
                            return;
                        } else if (state == BotState.WAITING_FOR_KEYWORD_2) {
                            saveKeyword(chatId, 1, messageText);
                            userStates.put(chatId, BotState.AUTHORIZED);
                            sendKeywordsMenu(chatId);
                            return;
                        } else if (state == BotState.WAITING_FOR_KEYWORD_3) {
                            saveKeyword(chatId, 2, messageText);
                            userStates.put(chatId, BotState.AUTHORIZED);
                            sendKeywordsMenu(chatId);
                            return;
                        } else if (state == BotState.WAITING_FOR_KEYWORD_4) {
                            saveKeyword(chatId, 3, messageText);
                            userStates.put(chatId, BotState.AUTHORIZED);
                            sendKeywordsMenu(chatId);
                            return;
                        } else if (state == BotState.WAITING_FOR_KEYWORD_5) {
                            saveKeyword(chatId, 4, messageText);
                            userStates.put(chatId, BotState.AUTHORIZED);
                            sendKeywordsMenu(chatId);
                            return;
                        }

                        // Если сообщение не из меню, запускаем текущий парсер как раньше
                        // Асинхронно запускаем парсинг
                        else {
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
                                    e.printStackTrace();
                                    sendMessage(chatId, "Произошла ошибка при поиске заказов.");
                                }
                            });

                            break;
                        }
                }
            }
            else {
                // На всякий случай, если состояние не AUTHORIZED, просим авторизоваться
                sendMessage(chatId, "Пожалуйста, авторизуйтесь командой /login");
            }
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



