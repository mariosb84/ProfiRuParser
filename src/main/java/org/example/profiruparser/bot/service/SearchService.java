package org.example.profiruparser.bot.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.parser.service.ProfiParserService;
import org.example.profiruparser.responder.ProfiResponder;
import org.example.profiruparser.service.SeenOrderService;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    @Value("${orderUrl}")
    private String orderUrl;

    private final ProfiParserService parser;
    private final ProfiResponder responder;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;
    private final SeenOrderService seenOrderService;


/* ИСПОЛЬЗУЕМ FIXED THREAD POOL ДЛЯ УПРАВЛЕНИЯ ПОТОКАМИ ПОИСКА */

    private final ExecutorService executor = Executors.newFixedThreadPool(4);


/* MAP ДЛЯ ХРАНЕНИЯ АКТИВНЫХ ПОИСКОВ ДЛЯ ВОЗМОЖНОСТИ ОТМЕНЫ */

    private final Map<Long, Future<?>> activeSearches = new ConcurrentHashMap<>();

    public void handleManualSearch(Long chatId, String query) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) return;

        if (!subscriptionService.isSubscriptionActive(user.getUsername())) {
            telegramService.sendMessage(chatId, "❌ Требуется активная подписка!");
            return;
        }


/* СОХРАНЯЕМ Future ДЛЯ ВОЗМОЖНОСТИ ОТМЕНЫ */

        Future<?> future = executor.submit(() -> {
            try {
                telegramService.sendMessage(chatId, "🔍 Идет поиск...");
                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                List<ProfiOrder> orders = parser.parseOrders(query);


/* ФИЛЬТРАЦИЯ: оставляем только новые заказы */

                List<ProfiOrder> newOrders = filterNewOrders(user.getId(), orders);

                if (newOrders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ Ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");


/* Сохраняем как просмотренные */

                    seenOrderService.markOrdersAsSeen(user.getId(),
                            newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));

                    newOrders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {

/* ПРОВЕРЯЕМ НЕ ОТМЕНЕН ЛИ ПОИСК */

                if (!Thread.currentThread().isInterrupted()) {
                    telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
                }
            } finally {

/* УДАЛЯЕМ ИЗ activeSearches ПОСЛЕ ЗАВЕРШЕНИЯ */

                activeSearches.remove(chatId);
            }
        });


/* СОХРАНЯЕМ Future ДЛЯ ВОЗМОЖНОСТИ ОТМЕНЫ */

        activeSearches.put(chatId, future);
    }

    public void searchByKeywords(Long chatId) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            telegramService.sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        if (!subscriptionService.isSubscriptionActive(user.getUsername())) {
            telegramService.sendMessage(chatId, "❌ Требуется активная подписка!");
            return;
        }

        List<String> keywords = stateManager.getUserKeywords(chatId);
        if (keywords == null || keywords.stream().allMatch(k -> k == null || k.trim().isEmpty())) {
            telegramService.sendMessage(chatId, "❌ Нет ключевых слов");
            return;
        }

        List<String> activeKeywords = keywords.stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .toList();


/* СОХРАНЯЕМ Future ДЛЯ ВОЗМОЖНОСТИ ОТМЕНЫ */

        Future<?> future = executor.submit(() -> {
            try {
                telegramService.sendMessage(chatId, "🚀 Идет поиск по " + activeKeywords.size() + " ключам...");


/* ПЕСОЧНЫЕ ЧАСЫ С MARKDOWN */

                SendMessage hourglassMessage = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("*⌛*")
                        .parseMode("Markdown")
                        .build();
                telegramService.sendMessage(hourglassMessage);

                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                LinkedHashSet<ProfiOrder> allOrders = new LinkedHashSet<>();

                for (String keyword : activeKeywords) {

/* ПРОВЕРЯЕМ НЕ ОТМЕНЕН ЛИ ПОИСК ПЕРЕД КАЖДЫМ КЛЮЧЕМ */

                    if (Thread.currentThread().isInterrupted()) {
                        log.info("Search interrupted for chatId: {}", chatId);
                        return;
                    }
                    allOrders.addAll(parser.parseOrders(keyword));
                    Thread.sleep(1000);
                }


/* ФИЛЬТРАЦИЯ: оставляем только новые заказы */

                List<ProfiOrder> newOrders = filterNewOrders(user.getId(), allOrders.stream().toList());

                if (newOrders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ По ключам ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");


/* Сохраняем как просмотренные */

                    seenOrderService.markOrdersAsSeen(user.getId(),
                            newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));

                    newOrders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {

/* ПРОВЕРЯЕМ НЕ ОТМЕНЕН ЛИ ПОИСК */

                if (!Thread.currentThread().isInterrupted()) {
                    telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
                }
            } finally {

/* УДАЛЯЕМ ИЗ activeSearches ПОСЛЕ ЗАВЕРШЕНИЯ */

                activeSearches.remove(chatId);
            }
        });


/* СОХРАНЯЕМ Future ДЛЯ ВОЗМОЖНОСТИ ОТМЕНЫ */

        activeSearches.put(chatId, future);
    }


/* НОВЫЙ МЕТОД: фильтрация просмотренных заказов */

    private List<ProfiOrder> filterNewOrders(Long userId, List<ProfiOrder> orders) {
        Set<String> seenOrderIds = seenOrderService.getSeenOrderIds(userId);

        return orders.stream()
                .filter(order -> !seenOrderIds.contains(order.getId()))
                .collect(Collectors.toList());
    }

    private void sendOrderCard(Long chatId, ProfiOrder order) {
        String orderUrl = this.orderUrl + order.getId();

        String text = String.format(
                "🆔 Заказ #%s\n📌 %s\n💰 %s\n📅 %s\n📝 %s\n\n⚠️ *Перед откликом убедитесь," +
                        " что вы авторизованы в Profi.ru в браузере! Либо придется первый раз авторизоваться.*",
                order.getId(), order.getTitle(), order.getPrice(), order.getCreationTime(),
                order.getDescription().length() > 1000 ?
                        order.getDescription().substring(0, 1000) + "..." : order.getDescription()
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        InlineKeyboardButton.builder()
                                .text("📱 Откликнуться")
                                .url(orderUrl)
                                .build()
                )
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(markup)
                .parseMode("Markdown")
/* Для жирного текста */

                .build();

        telegramService.sendMessage(message);
    }

    public boolean handleRespondToOrder(Long chatId, String orderId) {
        try {
            User user = userService.findByTelegramChatId(chatId);
            if (user == null) {
                log.error("User not found for chatId: {}", chatId);
                return false;
            }

            WebDriver driver = parser.getDriver();
            if (driver == null) {
                log.error("No active driver available");
                return false;
            }

            boolean success = responder.respondToOrder(driver, orderId, "Хочу выполнить заказ!");
            return success;
        } catch (Exception e) {
            log.error("Error responding to order: {}", e.getMessage());
            return false;
        }
    }


/* МЕТОД ДЛЯ ОТМЕНЫ АКТИВНОГО ПОИСКА */

    public void cancelSearch(Long chatId) {
        Future<?> future = activeSearches.get(chatId);
        if (future != null) {
            future.cancel(true);
            activeSearches.remove(chatId);
            log.info("Search cancelled for chatId: {}", chatId);
        }
    }


/* ЗАКРЫВАЕМ EXECUTOR SERVICE ПРИ ЗАВЕРШЕНИИ ПРИЛОЖЕНИЯ */

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Для очереди - прямой вызов без очереди
     */
    public void executeManualSearch(Long chatId, String query) {
        /* Переносим сюда код из handleManualSearch без executor.submit*/
        try {
            User user = userService.findByTelegramChatId(chatId);
            if (user == null) return;

            telegramService.sendMessage(chatId, "🔍 Идет поиск...");
            parser.ensureLoggedIn(user.getUsername(), user.getPassword());
            List<ProfiOrder> orders = parser.parseOrders(query);
            List<ProfiOrder> newOrders = filterNewOrders(user.getId(), orders);

            if (newOrders.isEmpty()) {
                telegramService.sendMessage(chatId, "❌ Ничего не найдено");
            } else {
                telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");
                seenOrderService.markOrdersAsSeen(user.getId(),
                        newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));
                newOrders.forEach(order -> sendOrderCard(chatId, order));
            }
        } catch (Exception e) {
            telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
        }
    }

    /**
     * Для очереди - прямой вызов поиска по ключам
     */
    public void executeKeywordSearch(Long chatId) {
        /* Переносим сюда код из searchByKeywords без executor.submit*/
        try {
            User user = userService.findByTelegramChatId(chatId);
            if (user == null) {
                telegramService.sendMessage(chatId, "❌ Пользователь не найден");
                return;
            }

            telegramService.sendMessage(chatId, "🚀 Идет поиск по ключевым словам...");

            SendMessage hourglassMessage = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("*⌛*")
                    .parseMode("Markdown")
                    .build();
            telegramService.sendMessage(hourglassMessage);

            parser.ensureLoggedIn(user.getUsername(), user.getPassword());
            LinkedHashSet<ProfiOrder> allOrders = new LinkedHashSet<>();

            List<String> keywords = stateManager.getUserKeywords(chatId);
            List<String> activeKeywords = keywords.stream()
                    .filter(k -> k != null && !k.trim().isEmpty())
                    .toList();

            for (String keyword : activeKeywords) {
                allOrders.addAll(parser.parseOrders(keyword));
                Thread.sleep(1000);
            }

            List<ProfiOrder> newOrders = filterNewOrders(user.getId(), allOrders.stream().toList());

            if (newOrders.isEmpty()) {
                telegramService.sendMessage(chatId, "❌ По ключам ничего не найдено");
            } else {
                telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");
                seenOrderService.markOrdersAsSeen(user.getId(),
                        newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));
                newOrders.forEach(order -> sendOrderCard(chatId, order));
            }
        } catch (Exception e) {
            telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
        }
    }

}

