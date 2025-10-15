package org.example.profiruparser.bot.keyboards;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuFactoryImpl implements MenuFactory {

    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;

    @Override
    public SendMessage createWelcomeMenu(Long chatId) {
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

        return message;
    }

    @Override
    public SendMessage createMainMenu(Long chatId) {
        User user = userService.findByTelegramChatId(chatId);
        String status = user != null ? getSubscriptionStatus(user.getUsername()) : "❌ Подписка: не активна";

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

        return message;
    }

    @Override
    public SendMessage createSubscriptionMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💳 *Выбор подписки*\n\n" +
                "✅ Неограниченный поиск\n" +
                "✅ Автопоиск по ключам\n" +
                "✅ Быстрые отклики\n\n" +
                "*После оплаты подписка активируется автоматически!*");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("1 месяц - 299₽"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("12 месяцев - 2490₽"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("✅ Проверить оплату"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    @Override
    public SendMessage createKeywordsMenu(Long chatId, List<String> keywords) {
        StringBuilder text = new StringBuilder("⚙️ *Ключевые слова:*\n\n");
        for (int i = 0; i < 5; i++) {
            String keyword = keywords.get(i);
            text.append(i + 1).append(". ").append(keyword.isEmpty() ? "не задан" : keyword).append("\n");
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

        return message;
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

}