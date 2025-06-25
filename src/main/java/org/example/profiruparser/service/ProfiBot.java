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

    // Храним состояние авторизации и этапы ввода для каждого пользователя
    private enum BotState {
        NONE,
        WAITING_FOR_USERNAME,
        WAITING_FOR_PASSWORD,
        AUTHORIZED
    }

    // Для каждого chatId хранит состояние
    private final Map<Long, BotState> userStates = new HashMap<>();
    // Для временного хранения логина при вводе пароля
    private final Map<Long, String> tempLogins = new HashMap<>();

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
                    case "Выйти":
                        userStates.put(chatId, BotState.NONE);
                        sendMessage(chatId, "Вы вышли из системы. Для повторной авторизации используйте /login");
                        break;
                    default:
                        // Если сообщение не из меню, запускаем текущий парсер как раньше
                        // Асинхронно запускаем парсинг
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
            } else {
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
        return "Заказ #" + order.getId() + "\n"
                + "Название: " + order.getTitle() + "\n"
                + "Цена: " + order.getPrice();
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


}



