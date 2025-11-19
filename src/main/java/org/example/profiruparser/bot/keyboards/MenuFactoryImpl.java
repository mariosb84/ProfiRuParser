package org.example.profiruparser.bot.keyboards;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.constants.TextConstants;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

/*import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;*/
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuFactoryImpl implements MenuFactory {

    @Value("${app.subscription.monthly.price}")
    private String monthlyPrice;

    @Value("${app.subscription.yearly.price}")
    private String yearlyPrice;

    @Value("${currencySecond}")
    private String currencySecond;

    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;

    @Override
    public SendMessage createWelcomeMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👋 Добро пожаловать!\n\n"
                + TextConstants.START_TEXT.getText()
                + "\n\nВыберите действие:"
        );

        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📝 Подключить_Profi_ru"));
        row1.add(new KeyboardButton("🔑 Войти"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📋 Информация"));    /* ← ДОБАВЛЯЕМ*/
        row2.add(new KeyboardButton("📞 Контакты"));      /* ← ДОБАВЛЯЕМ*/

        KeyboardRow row3 = new KeyboardRow();
        row2.add(new KeyboardButton("🏠 Старт"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    @Override
    public SendMessage createMainMenu(Long chatId) {
        return createMainMenu(chatId, false); /* вызов перегруженного метода с флагом false*/
    }

    /* НОВЫЙ ПЕРЕГРУЖЕННЫЙ МЕТОД*/
    public SendMessage createMainMenu(Long chatId, boolean afterSearch) {
        User user = userService.findByTelegramChatId(chatId);
        String status = "";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        /* Показываем надпись о выходе в главное меню только если НЕ после поиска*/
        if (!afterSearch) {
            status = user != null ? getSubscriptionStatus(user.getUsername()) : "❌ Подписка: не активна";
            message.setText("🏠 *Главное меню*\n\n" + status + "\n\nВыберите действие:");
            message.setParseMode("Markdown");
        } else {
            /* После поиска - показываем сообщение о поиске*/
            /*message.setText("*Время ожидания зависит от загрузки сервера...*");*/
            message.setText("*⌛*");
            message.setParseMode("Markdown");
        }

        /* Остальная логика создания клавиатуры БЕЗ ИЗМЕНЕНИЙ*/
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
        row3.add(new KeyboardButton("📋 Информация"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("📞 Контакты"));
        row4.add(new KeyboardButton("🏠 Главное меню"));

        KeyboardRow row5 = new KeyboardRow();
        row5.add(new KeyboardButton("❌ Выйти"));

        /* В списке rows после row5 добавляем:*/
        KeyboardRow row6 = new KeyboardRow();
        row6.add(new KeyboardButton("⚙️ Сменить данные Profi_ru"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        rows.add(row5);
        /* И добавляем в rows:*/
        rows.add(row6);
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
                "*После оплаты подписка активируется автоматически" +
                " втечение 59 секунд!*");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();

       /* row1.add(new KeyboardButton("1 месяц - 299₽"));*/ /* меняем на @Value*/
        row1.add(new KeyboardButton("1 месяц - " + this.monthlyPrice + this.currencySecond));

        /*KeyboardRow row2 = new KeyboardRow();*/

        /*row2.add(new KeyboardButton("12 месяцев - 2490₽"));*/ /* меняем на @Value*/
       /* row2.add(new KeyboardButton("12 месяцев - " + this.yearlyPrice + this.currencySecond));*/ /*ПОКА УБИРАЕМ ПОДПИСКУ НА 1 ГОД*/

        /*KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("✅ Проверить оплату"));*/  /* пока  убираем ручную проверку оплаты*/

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        rows.add(row2);
       /* rows.add(row3);*/
        /*rows.add(row4);*/
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
        return subscriptionService.getSubscriptionStatus(username); /* ← ВОТ ТАК*/
    }

    @Override
    public SendMessage createInfoMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📋 *Информация*\n\n" +
                "Здесь будет основная информация о боте...\n\n" +
                TextConstants.INFO_TEXT.getText());
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    @Override
    public SendMessage createContactsMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📞 *Контакты*\n\n" +
                TextConstants.CONTACTS_TEXT.getText());

        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🔙 Назад"));

        rows.add(row1);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

}
