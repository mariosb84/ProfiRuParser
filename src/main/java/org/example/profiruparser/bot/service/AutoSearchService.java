package org.example.profiruparser.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSearchService {

    private final SearchService searchService;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;

    private final ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    /** MAP ДЛЯ ОТСЛЕЖИВАНИЯ БЫЛ ЛИ ВКЛЮЧЕН АВТОПОИСК ДЛЯ КАЖДОГО ПОЛЬЗОВАТЕЛЯ */
    private final Map<Long, Boolean> userAutoSearchStatus = new ConcurrentHashMap<>();

    public void handleAutoSearchCommand(Long chatId) {
        stateManager.setUserState(chatId, UserStateManager.STATE_AUTO_SEARCH);
        sendAutoSearchMenuWithStatus(chatId);
    }

    public void handleEnableAutoSearch(Long chatId) {
        stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_INTERVAL);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⏰ *Настройка автопоиска*\n\n" +
                "Введите интервал в минутах (например, 60 для 1 часа)\n" +
                "Или выберите один из стандартных интервалов:");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("30 мин"));
        row1.add(new KeyboardButton("60 мин"));
        row1.add(new KeyboardButton("120 мин"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        telegramService.sendMessage(message);
    }

    public void handleIntervalInput(Long chatId, String text) {
        try {
            int interval;

            /* ЕСЛИ НАЖАТА КНОПКА 30/60/120 МИН*/
            if (text.equals("30 мин") || text.equals("60 мин") || text.equals("120 мин")) {
                interval = Integer.parseInt(text.replace(" мин", ""));
            } else {
                /* ЕСЛИ ВВЕДЕНО ЧИСЛО ВРУЧНУЮ*/
                interval = Integer.parseInt(text);
            }

            if (interval < 15) {
                telegramService.sendMessage(chatId, "❌ Минимальный интервал - 15 минут");
                return;
            }
            if (interval > 1440) {
                telegramService.sendMessage(chatId, "❌ Максимальный интервал - 24 часа (1440 минут)");
                return;
            }

            startAutoSearch(chatId, interval);
            stateManager.setUserState(chatId, UserStateManager.STATE_AUTO_SEARCH);
            telegramService.sendMessage(chatId, "✅ Автопоиск включен! Первая проверка через " + interval + " минут");
            sendAutoSearchMenuWithStatus(chatId);

        } catch (NumberFormatException e) {
            telegramService.sendMessage(chatId, "❌ Введите число (интервал в минутах):");
        }
    }

    /** МЕТОД ДЛЯ ОТКЛЮЧЕНИЯ АВТОПОИСКА С ПРОВЕРКОЙ БЫЛ ЛИ ОН ВКЛЮЧЕН (с обновлением меню) */
    public void handleDisableAutoSearch(Long chatId) {
        handleDisableAutoSearch(chatId, true); /* по умолчанию обновляем меню*/
    }

    /** МЕТОД ДЛЯ ОТКЛЮЧЕНИЯ АВТОПОИСКА С ПРОВЕРКОЙ БЫЛ ЛИ ОН ВКЛЮЧЕН */
    public void handleDisableAutoSearch(Long chatId, boolean updateMenu) {
        /** ПРОВЕРЯЕМ БЫЛ ЛИ ВКЛЮЧЕН АВТОПОИСК (по наличию в scheduledTasks) */
        boolean wasEnabled = scheduledTasks.containsKey(chatId);

        /** ОСТАНАВЛИВАЕМ АВТОПОИСК */
        stopAutoSearch(chatId);

        /** СООБЩЕНИЕ ТОЛЬКО ЕСЛИ БЫЛ ВКЛЮЧЕН */
        if (wasEnabled) {
            telegramService.sendMessage(chatId, "⏰ ⏹️ Автопоиск отключен");
            log.info("AutoSearch disabled for chatId: {}", chatId);
        }
        /** ЕСЛИ НЕ БЫЛ ВКЛЮЧЕН - НИЧЕГО НЕ ДЕЛАЕМ И НЕ ОТПРАВЛЯЕМ СООБЩЕНИЕ */

        /** ВСЕГДА ОБНОВЛЯЕМ МЕНЮ ЧТОБЫ КНОПКА ИЗМЕНИЛАСЬ */

        /** ОБНОВЛЯЕМ МЕНЮ ТОЛЬКО ЕСЛИ НУЖНО */
        if (updateMenu) {
            sendAutoSearchMenuWithStatus(chatId);
        }
    }

    /** ИЗМЕНЯЕМ НА PUBLIC ЧТОБЫ БЫЛ ДОСТУПЕН ИЗНУТРИ КЛАССА */
    public void startAutoSearch(Long chatId, int intervalMinutes) {
        stopAutoSearch(chatId);

        User user = userService.findByTelegramChatId(chatId);
        if (user == null || !subscriptionService.isSubscriptionActive(user.getUsername())) {
            telegramService.sendMessage(chatId, "❌ Автопоиск остановлен - требуется активная подписка");
            return;
        }

        final String username = user.getUsername();

        /** ИЗМЕНЕНИЕ: delay = intervalMinutes, period = intervalMinutes
         Теперь первый поиск через intervalMinutes, а не сразу*/
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!subscriptionService.isSubscriptionActive(username)) {
                    telegramService.sendMessage(chatId, "❌ Автопоиск остановлен - подписка истекла");
                    stopAutoSearch(chatId);
                    return;
                }

                searchService.searchByKeywords(chatId);

            } catch (Exception e) {
                log.error("Ошибка в автопоиске: {}", e.getMessage());
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES); /* ← delay = intervalMinutes*/

        scheduledTasks.put(chatId, future);
        /** СОХРАНЯЕМ СТАТУС АВТОПОИСКА */
        userAutoSearchStatus.put(chatId, true);
        stateManager.setUserInterval(chatId, intervalMinutes);
    }

    public void stopAutoSearch(Long chatId) {
        ScheduledFuture<?> future = scheduledTasks.get(chatId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(chatId);
            userAutoSearchStatus.remove(chatId); /** ОЧИЩАЕМ СТАТУС */
        }
        stateManager.removeUserInterval(chatId);
    }

    public void sendAutoSearchMenuWithStatus(Long chatId) {
        boolean isAutoSearchRunning = scheduledTasks.containsKey(chatId);
        Integer currentInterval = stateManager.getUserInterval(chatId);

        String status;
        if (isAutoSearchRunning && currentInterval != null) {
            status = "✅ Включен (интервал: " + currentInterval + " мин.)";
        } else {
            status = "❌ Выключен";
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⏰ *Настройки автопоиска*\n\nТекущий статус: " + status +
                "\n\nАвтопоиск будет проверять заказы по вашим ключевым словам автоматически.");
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

        telegramService.sendMessage(message);
    }

    public void shutdown() {
        scheduler.shutdown();
        scheduledTasks.values().forEach(future -> future.cancel(false));
        userAutoSearchStatus.clear(); /** ОЧИЩАЕМ СТАТУСЫ ПРИ ЗАВЕРШЕНИИ */
    }

    public void handleIntervalButton(Long chatId, String text) {
        boolean isAutoSearchRunning = scheduledTasks.containsKey(chatId);

        if (!isAutoSearchRunning) {
            /* ЕСЛИ АВТОПОИСК ВЫКЛЮЧЕН - ПРЕДЛАГАЕМ ВКЛЮЧИТЬ*/
            telegramService.sendMessage(chatId, "❌ Автопоиск выключен. Нажмите '🔔 Включить автопоиск' для настройки.");
            return;
        }

        /* ЕСЛИ АВТОПОИСК ВКЛЮЧЕН - МЕНЯЕМ ИНТЕРВАЛ*/
        int interval = Integer.parseInt(text.replace(" мин", ""));

        /* ОСТАНАВЛИВАЕМ СТАРЫЙ И ЗАПУСКАЕМ С НОВЫМ ИНТЕРВАЛОМ*/
        stopAutoSearch(chatId);
        startAutoSearch(chatId, interval);

        telegramService.sendMessage(chatId, "✅ Интервал автопоиска изменен на " + interval + " минут");
        sendAutoSearchMenuWithStatus(chatId);
    }

    /** Метод проверки был ли включен автопоиск */
    public boolean isAutoSearchRunning(Long chatId) {
        return scheduledTasks.containsKey(chatId);
    }

}
