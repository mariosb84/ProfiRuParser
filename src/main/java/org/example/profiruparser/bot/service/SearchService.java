package org.example.profiruparser.bot.service;

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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    @Value("${orderUrl}")
    private String orderUrl ;

    private final ProfiParserService parser;
    private final ProfiResponder responder;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;
    private final SeenOrderService seenOrderService; // ДОБАВЛЯЕМ

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

                /* ФИЛЬТРАЦИЯ: оставляем только новые заказы*/
                List<ProfiOrder> newOrders = filterNewOrders(user.getId(), orders);

                if (newOrders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ Ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");

                    /* Сохраняем как просмотренные*/
                    seenOrderService.markOrdersAsSeen(user.getId(),
                            newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));

                    newOrders.forEach(order -> sendOrderCard(chatId, order));
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

                /* ПЕСОЧНЫЕ ЧАСЫ С MARKDOWN*/
                SendMessage hourglassMessage = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("*⌛*")
                        .parseMode("Markdown")
                        .build();
                telegramService.sendMessage(hourglassMessage);

                parser.ensureLoggedIn(user.getUsername(), user.getPassword());
                LinkedHashSet<ProfiOrder> allOrders = new LinkedHashSet<>();

                for (String keyword : activeKeywords) {
                    allOrders.addAll(parser.parseOrders(keyword));
                    Thread.sleep(1000);
                }

                /* ФИЛЬТРАЦИЯ: оставляем только новые заказы*/
                List<ProfiOrder> newOrders = filterNewOrders(user.getId(), allOrders.stream().toList());

                if (newOrders.isEmpty()) {
                    telegramService.sendMessage(chatId, "❌ По ключам ничего не найдено");
                } else {
                    telegramService.sendMessage(chatId, "✅ Найдено: " + newOrders.size() + " заказов");

                    /* Сохраняем как просмотренные*/
                    seenOrderService.markOrdersAsSeen(user.getId(),
                            newOrders.stream().map(ProfiOrder::getId).collect(Collectors.toList()));

                    newOrders.forEach(order -> sendOrderCard(chatId, order));
                }
            } catch (Exception e) {
                telegramService.sendMessage(chatId, "❌ Ошибка поиска: " + e.getMessage());
            }
        });
    }

    /* НОВЫЙ МЕТОД: фильтрация просмотренных заказов*/
    private List<ProfiOrder> filterNewOrders(Long userId, List<ProfiOrder> orders) {
        Set<String> seenOrderIds = seenOrderService.getSeenOrderIds(userId);

        return orders.stream()
                .filter(order -> !seenOrderIds.contains(order.getId()))
                .collect(Collectors.toList());
    }

    private void sendOrderCard(Long chatId, ProfiOrder order) {
        /*String orderUrl = "https://profi.ru/backoffice/n.php?o=" + order.getId();*/ /* меняем на @Value*/
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
                .parseMode("Markdown")  /* Для жирного текста*/
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

}
