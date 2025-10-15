package org.example.profiruparser.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.ProfiParser;
import org.example.profiruparser.service.ProfiResponder;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ProfiParser parser;
    private final ProfiResponder responder;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void handleManualSearch(Long chatId, String query) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) return;

        if (!subscriptionService.isSubscriptionActive(user.getUsername())) {
            telegramService.sendMessage(chatId, "❌ Требуется активная подписка!");
            return;
        }

        executor.submit(() -> {
            try {
                telegramService.sendMessage(chatId, "🔍 Идет поиск...");
                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                List<ProfiOrder> orders = parser.parseOrders(query);

                if (orders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ Ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + orders.size() + " заказов");
                    orders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {
                telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
            }
        });
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

        executor.submit(() -> {
            try {
                telegramService.sendMessage(chatId, "🚀 Идет поиск по " + activeKeywords.size() + " ключам...");
                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                LinkedHashSet<ProfiOrder> allOrders = new LinkedHashSet<>();

                for (String keyword : activeKeywords) {
                    allOrders.addAll(parser.parseOrders(keyword));
                    Thread.sleep(1000);
                }

                if (allOrders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ По ключам ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + allOrders.size() + " заказов");
                    allOrders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {
                telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
            }
        });
    }

    private void sendOrderCard(Long chatId, ProfiOrder order) {
        String text = String.format(
                "🆔 Заказ #%s\n📌 %s\n💰 %s\n📅 %s\n📝 %s",
                order.getId(), order.getTitle(), order.getPrice(), order.getCreationTime(),
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


        // ДОБАВЬ ЭТУ ПАУЗУ
        try {
            Thread.sleep(300); // 300ms между сообщениями
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        telegramService.sendMessage(message);
    }

        public boolean handleRespondToOrder(String orderId) {
        try {
            boolean success = responder.respondToOrder(orderId, "Хочу выполнить заказ!");
            return success;
        } catch (Exception e) {
            log.error("Error responding to order: {}", e.getMessage());
            return false;
        }
    }

}

