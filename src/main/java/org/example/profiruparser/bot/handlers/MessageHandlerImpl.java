package org.example.profiruparser.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.keyboards.MenuFactory;
import org.example.profiruparser.bot.service.*;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.parser.service.ProfiParserService;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandlerImpl implements MessageHandler {

    @Value("${app.subscription.monthly.price}")
    private String monthlyPrice;

    @Value("${app.subscription.yearly.price}")
    private String yearlyPrice;

    @Value("${currencySecond}")
    private String currencySecond;

    private final AuthService authService;
    private final SearchService searchService;
    private final KeywordService keywordService;
    private final AutoSearchService autoSearchService;
    private final PaymentHandler paymentHandler;
    private final UserStateManager stateManager;
    private final UserServiceData userService;
    private final SubscriptionService subscriptionService;
    private final TelegramService telegramService;
    private final MenuFactory menuFactory;
    private final ProfiParserService parser;

    private final SearchQueueService searchQueueService;

    @Override
    public void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        String userState = stateManager.getUserState(chatId);

        log.debug("Handling message - ChatId: {}, Text: {}, State: {}", chatId, text, userState);

        try {
            /* Обработка состояний ввода*/
            if (handleInputStates(chatId, text, userState)) {
                return;
            }

            /* Обработка команд*/
            handleCommand(chatId, text);

        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage());
            telegramService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте еще раз.");
        }
    }

    private boolean handleInputStates(Long chatId, String text, String userState) {
        /* ГЛОБАЛЬНЫЕ КОМАНДЫ - РАБОТАЮТ В ЛЮБОМ СОСТОЯНИИ*/
        if (text.equals("/start") || text.equals("🏠 Старт")) {
            handleStartCommand(chatId);
            return true;
        }

        /* ГЛОБАЛЬНЫЕ КНОПКИ МЕНЮ - ВСЕГДА ВОЗВРАЩАЮТ В ПРАВИЛЬНОЕ МЕНЮ*/
        if (text.equals("🔙 Назад") || text.equals("🏠 Главное меню")) {

            /* ЕСЛИ МЫ В ПРОЦЕССЕ СМЕНЫ ДАННЫХ - ОБРАБАТЫВАЕМ КАК ОТМЕНУ*/
            if (userState.equals(UserStateManager.STATE_CHANGE_CREDENTIALS_USERNAME) ||
                    userState.equals(UserStateManager.STATE_CHANGE_CREDENTIALS_PASSWORD)) {

                telegramService.sendMessage(chatId, "❌ Смена данных отменена");
                stateManager.removeTempUsername(chatId);
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                sendMainMenu(chatId, false);
                return true;
            }

            /* ВОЗВРАЩАЕМ В ПРАВИЛЬНОЕ МЕНЮ В ЗАВИСИМОСТИ ОТ АВТОРИЗАЦИИ*/
            if (isUserAuthorized(chatId)) {
                /*sendMainMenu(chatId);*/
                sendMainMenu(chatId, false); /* ← ОСТАВИТЬ false*/
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
            } else {
                sendWelcomeMenu(chatId);
                stateManager.setUserState(chatId, UserStateManager.STATE_NONE);
            }
            return true;
        }

        /* БЛОКИРОВКА ВСЕХ КНОПОК МЕНЮ ВО ВРЕМЯ ВВОДА ПОИСКОВОГО ЗАПРОСА*/
        if (userState.equals(UserStateManager.STATE_WAITING_SEARCH_QUERY)) {
            if (isMenuCommand(text)) {
                telegramService.sendMessage(chatId,
                        /*"❌ Завершите ввод поискового запроса или нажмите '🔙 Назад' для отмены");*/
                          "❌ Завершите ввод поискового запроса или нажмите '🏠 Главное меню' для отмены");
                return true;
            }
        }

        /* БЛОКИРОВКА КНОПОК ПРИ СМЕНЕ ДАННЫХ*/
        if (userState.equals(UserStateManager.STATE_CHANGE_CREDENTIALS_USERNAME) ||
                userState.equals(UserStateManager.STATE_CHANGE_CREDENTIALS_PASSWORD)) {

            if (isMenuCommand(text)) {
                telegramService.sendMessage(chatId, "❌ Завершите ввод данных или нажмите '🏠 Главное меню' для отмены");
                return true;
            }
        }

        /* БЛОКИРОВКА ВСЕХ КНОПОК МЕНЮ ВО ВРЕМЯ ВВОДА ДАННЫХ АВТОРИЗАЦИИ*/
        if (userState.equals(UserStateManager.STATE_WAITING_USERNAME) ||
                userState.equals(UserStateManager.STATE_WAITING_PASSWORD) ||
                userState.equals(UserStateManager.STATE_REGISTER_USERNAME) ||
                userState.equals(UserStateManager.STATE_REGISTER_PASSWORD)) {

            /* СПИСОК ЗАБЛОКИРОВАННЫХ КНОПОК ВО ВРЕМЯ ВВОДА*/
            if (text.equals("🔑 Войти") ||
                    text.equals("📝 Подключить_Profi_ru") ||
                    text.equals("📋 Информация") ||      /* ← ДОБАВЛЯЕМ*/
                    text.equals("📞 Контакты")) {        /* ← ДОБАВЛЯЕМ*/

                telegramService.sendMessage(chatId, "❌ Завершите текущий процесс ввода данных");
                return true;
            }
        }

        if (text.equals( "❌ Выйти" )) {
                authService.handleLogout(chatId);
                return true;
            }

        /* КНОПКИ МЕНЮ КЛЮЧЕВЫХ СЛОВ - РАБОТАЮТ В ЛЮБОМ СОСТОЯНИИ ВВОДА*/
        if (userState.startsWith("WAITING_FOR_KEYWORD_")) {
            /* ЕСЛИ НАЖАТА КНОПКА МЕНЮ КЛЮЧЕВЫХ СЛОВ - ОБРАБАТЫВАЕМ ЕЕ*/
            if (text.startsWith("✏️ Ключ ") || text.equals("🧹 Очистить все") || text.equals("🚀 Поиск по ключам")) {
                /* СБРАСЫВАЕМ СОСТОЯНИЕ ВВОДА*/
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_KEYWORDS);
                /* ПЕРЕДАЕМ УПРАВЛЕНИЕ В handleAuthorizedCommand*/
                handleAuthorizedCommand(chatId, text);
                return true;
            }

            /* ЕСЛИ ЭТО ТЕКСТ ДЛЯ ВВОДА КЛЮЧА - ОБРАБАТЫВАЕМ*/
            try {
                keywordService.handleKeywordInput(chatId, text);
                List<String> keywords = keywordService.getKeywordsForDisplay(chatId);
                telegramService.sendMessage(menuFactory.createKeywordsMenu(chatId, keywords));
                return true;
            } catch (Exception e) {
                telegramService.sendMessage(chatId, e.getMessage());
                return true;
            }
        }

        /* ОСТАЛЬНЫЕ СОСТОЯНИЯ ВВОДА*/
        switch (userState) {
            case UserStateManager.STATE_WAITING_PAYMENT_ID:
                paymentHandler.handlePaymentCheck(chatId, text);
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                return true;

            case UserStateManager.STATE_WAITING_USERNAME:
                authService.handleUsernameInput(chatId, text, false);
                return true;

            case UserStateManager.STATE_WAITING_PASSWORD:
                authService.handlePasswordInput(chatId, text, false);
                return true;

            case UserStateManager.STATE_REGISTER_USERNAME:
                authService.handleUsernameInput(chatId, text, true);
                return true;

            case UserStateManager.STATE_REGISTER_PASSWORD:
                authService.handlePasswordInput(chatId, text, true);
                return true;

            case UserStateManager.STATE_WAITING_INTERVAL:
                autoSearchService.handleIntervalInput(chatId, text);
                return true;

            case UserStateManager.STATE_WAITING_SEARCH_QUERY:
                stateManager.setTempSearchQuery(chatId, text);
                stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_SEARCH_CONFIRMATION);

                SendMessage confirmMessage = new SendMessage();
                confirmMessage.setChatId(chatId.toString());
                confirmMessage.setText("🔍 *Найти заказы по запросу:*\n\"`" + text + "`\"\n\nНачать поиск?");
                confirmMessage.setParseMode("Markdown");

                ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
                keyboard.setResizeKeyboard(true);
                List<KeyboardRow> rows = new ArrayList<>();

                KeyboardRow row1 = new KeyboardRow();
                row1.add(new KeyboardButton("✅ Начать поиск"));
                row1.add(new KeyboardButton("❌ Отмена"));

                rows.add(row1);
                keyboard.setKeyboard(rows);
                confirmMessage.setReplyMarkup(keyboard);

                telegramService.sendMessage(confirmMessage);
                return true;

            /** В методе handleInputStates обновляем case для STATE_WAITING_SEARCH_CONFIRMATION */
            case UserStateManager.STATE_WAITING_SEARCH_CONFIRMATION:
                if (text.equals("✅ Начать поиск")) {
                    String searchQuery = stateManager.getTempSearchQuery(chatId);
                    /** ЗАПУСК РУЧНОГО ПОИСКА ЧЕРЕЗ ОЧЕРЕДЬ */
                    searchQueueService.addToQueue(chatId, searchQuery, SearchTask.SearchType.MANUAL);
                    stateManager.removeTempSearchQuery(chatId);
                    stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                    sendMainMenu(chatId, true);
                } else if (text.equals("❌ Отмена")) {
                    telegramService.sendMessage(chatId, "❌ Поиск отменен");
                    stateManager.removeTempSearchQuery(chatId);
                    stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                    sendMainMenu(chatId, false);
                }
                return true;

            /* Добавляем в switch (userState) после существующих case:*/
            case UserStateManager.STATE_CHANGE_CREDENTIALS_USERNAME:

                /* ПРОВЕРКА ДЛИНЫ ЛОГИНА*/
                if (text.length() < 3) {
                    telegramService.sendMessage(chatId, "❌ Логин должен содержать минимум 3 символа:");
                    return true;
                }

                stateManager.setTempUsername(chatId, text);
                stateManager.setUserState(chatId, UserStateManager.STATE_CHANGE_CREDENTIALS_PASSWORD);
                telegramService.sendMessage(chatId, "🔑 Введите новый пароль для Profi_ru:");
                return true;

            case UserStateManager.STATE_CHANGE_CREDENTIALS_PASSWORD:
                handleChangeCredentials(chatId, stateManager.getTempUsername(chatId), text);
                stateManager.removeTempUsername(chatId);
                stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_MAIN);
                return true;

            default:
                return false;
        }
    }

    private void handleChangeCredentials(Long chatId, String newUsername, String newPassword) {
        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            telegramService.sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        /* Обновляем только логин и пароль, сохраняя подписку*/
        user.setUsername(newUsername);
        user.setPassword(newPassword);
        userService.save(user);

        telegramService.sendMessage(chatId, "✅ Данные Profi_ru успешно обновлены!");
        sendMainMenu(chatId, false);
    }

    private void handleCommand(Long chatId, String text) {
        /* ОБРАБАТЫВАЕМ КОМАНДУ СТАРТ ВНЕ ЗАВИСИМОСТИ ОТ СОСТОЯНИЯ*/
        if (text.equals("/start") || text.equals("🏠 Старт")) {
            handleStartCommand(chatId);
            return;
        }

        switch (text) {
            case "/register", "📝 Подключить_Profi_ru":
                authService.handleRegisterCommand(chatId);
                break;
            case "/login", "🔑 Войти":
                authService.handleLoginCommand(chatId);
                break;

            case "📋 Информация":                    /* ← ДОБАВЛЯЕМ*/
                sendInfoMenu(chatId);
                break;
            case "📞 Контакты":                     /* ← ДОБАВЛЯЕМ*/
                sendContactsMenu(chatId);
                break;

            case "✅ Проверить оплату":
                handleCheckPaymentCommand(chatId);
                break;
            default:
                handleAuthorizedCommand(chatId, text);
        }
    }

    private void handleStartCommand(Long chatId) {
        stateManager.clearUserData(chatId);
        /*paymentHandler.checkAutoPayment(chatId);*/  /* УБИРАЕМ ПОКА ЭТОТ МЕТОД ПРИ СТАРТЕ*/

        if (isUserAuthorized(chatId)) {
            sendMainMenu(chatId);
        } else {
            sendWelcomeMenu(chatId);
        }
    }

    private void handleAuthorizedCommand(Long chatId, String text) {
        /** ПРОВЕРКА АВТОРИЗАЦИИ ПОЛЬЗОВАТЕЛЯ */
        if (!isUserAuthorized(chatId)) {
            telegramService.sendMessage(chatId, "Пожалуйста, авторизуйтесь: /login");
            return;
        }

        User user = userService.findByTelegramChatId(chatId);
        if (user == null) {
            telegramService.sendMessage(chatId, "❌ Пользователь не найден");
            return;
        }

        String userState = stateManager.getUserState(chatId);

        /**
         * ЕСЛИ НЕ КОМАНДА МЕНЮ И МЫ В СОСТОЯНИИ ВВОДА ПОИСКА - ЭТО ПОИСКОВЫЙ ЗАПРОС
         * Обрабатываем текст как поисковый запрос
         */
        if (!isMenuCommand(text) && UserStateManager.STATE_WAITING_SEARCH_QUERY.equals(userState)) {
            /** Сохраняем запрос и показываем подтверждение */
            stateManager.setTempSearchQuery(chatId, text);
            stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_SEARCH_CONFIRMATION);

            SendMessage confirmMessage = new SendMessage();
            confirmMessage.setChatId(chatId.toString());
            confirmMessage.setText("🔍 *Найти заказы по запросу:*\n\"`" + text + "`\"\n\nНачать поиск?");
            confirmMessage.setParseMode("Markdown");

            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            keyboard.setResizeKeyboard(true);
            List<KeyboardRow> rows = new ArrayList<>();

            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton("✅ Начать поиск"));
            row1.add(new KeyboardButton("❌ Отмена"));

            rows.add(row1);
            keyboard.setKeyboard(rows);
            confirmMessage.setReplyMarkup(keyboard);

            telegramService.sendMessage(confirmMessage);
            return;
        }

        /**
         * ЕСЛИ НЕ КОМАНДА МЕНЮ И МЫ В ГЛАВНОМ МЕНЮ - ЭТО НЕИЗВЕСТНАЯ КОМАНДА
         * Пользователь ввел непонятный текст в главном меню
         */
        if (!isMenuCommand(text) && UserStateManager.STATE_AUTHORIZED_MAIN.equals(userState)) {
            telegramService.sendMessage(chatId, "Неизвестная команда");
            return;
        }

        /** ПРОВЕРКА ПОДПИСКИ ДЛЯ ПЛАТНЫХ ФУНКЦИЙ */
        if (!subscriptionService.isSubscriptionActive(user.getUsername()) && !isFreeCommand(text)) {
            telegramService.sendMessage(chatId, "❌ Требуется активная подписка!");
            sendSubscriptionMenu(chatId);
            return;
        }

        /** ОБРАБОТКА КОМАНД МЕНЮ */
        if ("🔍 Ручной поиск".equals(text)) {
            /** Переход в состояние ввода поискового запроса */
            stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_SEARCH_QUERY);
            telegramService.sendMessage(chatId, "Введите поисковый запрос:");

        } else if ("⚙️ Ключевые слова".equals(text)) {
            /** Переход в меню управления ключевыми словами */
            stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_KEYWORDS);
            List<String> keywords = keywordService.getKeywordsForDisplay(chatId);
            telegramService.sendMessage(menuFactory.createKeywordsMenu(chatId, keywords));

        } else if ("🚀 Поиск по ключам".equals(text)) {
            /** ЗАПУСК ПОИСКА ПО КЛЮЧЕВЫМ СЛОВАМ ЧЕРЕЗ ОЧЕРЕДЬ */
            searchQueueService.addToQueue(chatId, null, SearchTask.SearchType.KEYWORDS);

        } else if ("💳 Оплатить подписку".equals(text)) {
            /** Переход в меню оплаты подписки */
            sendSubscriptionMenu(chatId);

        } else if (("1 месяц - " + this.monthlyPrice + this.currencySecond).equals(text)) {
            /** Обработка оплаты месячной подписки */
            paymentHandler.handleSubscriptionPayment(chatId, "MONTHLY");

        } else if (("12 месяцев - " + this.yearlyPrice + this.currencySecond).equals(text)) {
            /** Обработка оплаты годовой подписки */
            paymentHandler.handleSubscriptionPayment(chatId, "YEARLY");

        } else if ("🧹 Очистить все".equals(text)) {
            /** Очистка всех ключевых слов */
            keywordService.clearAllKeywords(chatId);
            List<String> clearedKeywords = keywordService.getKeywordsForDisplay(chatId);
            telegramService.sendMessage(menuFactory.createKeywordsMenu(chatId, clearedKeywords));

        } else if ("🔙 Назад".equals(text)) {
            /** Возврат в главное меню */
            sendMainMenu(chatId, false);

        } else if ("🏠 Главное меню".equals(text)) {
            /** Возврат в главное меню */
            sendMainMenu(chatId, false);

        } else if ("📋 Информация".equals(text)) {
            /** Переход в меню информации */
            sendInfoMenu(chatId);

        } else if ("📞 Контакты".equals(text)) {
            /** Переход в меню контактов */
            sendContactsMenu(chatId);

        } else if ("⏰ Автопоиск".equals(text)) {
            /** Переход в настройки автопоиска */
            autoSearchService.handleAutoSearchCommand(chatId);

        } else if ("🔔 Включить автопоиск".equals(text)) {
            /** Включение автопоиска */
            autoSearchService.handleEnableAutoSearch(chatId);

        } else if ("🔕 Выключить автопоиск".equals(text)) {
            /** Выключение автопоиска */
            autoSearchService.handleDisableAutoSearch(chatId);

        } else if ("30 мин".equals(text) || "60 мин".equals(text) || "120 мин".equals(text)) {
            /** Обработка кнопок интервалов автопоиска */
            autoSearchService.handleIntervalButton(chatId, text);

        } else if ("✅ Начать поиск".equals(text) || "❌ Отмена".equals(text)) {
            /**
             * Эти кнопки обрабатываются выше в состоянии подтверждения
             * ничего не делаем, т.к. обрабатывается в другом месте
             */

        } else if ("❌ Выйти".equals(text)) {
            /** Выход из аккаунта */
            authService.handleLogout(chatId);

        } else if (text.startsWith("✏️ Ключ ")) {
            /** Редактирование конкретного ключевого слова */
            keywordService.handleEditKeywordCommand(chatId, text);

        } else if ("⚙️ Сменить данные Profi_ru".equals(text)) {
            /** Смена логина и пароля Profi.ru */
            telegramService.sendMessage(chatId, "✏️ Введите новый логин для Profi_ru:");
            stateManager.setUserState(chatId, UserStateManager.STATE_CHANGE_CREDENTIALS_USERNAME);

        } else {
            /** Неизвестная команда */
            telegramService.sendMessage(chatId, "Неизвестная команда");
        }
    }

    /* ДОБАВИТЬ МЕТОД ПРОВЕРКИ КОМАНД МЕНЮ*/
    private boolean isMenuCommand(String text) {
        return text.equals("🔍 Ручной поиск") ||
                text.equals("⚙️ Ключевые слова") ||
                text.equals("🚀 Поиск по ключам") ||
                text.equals("💳 Оплатить подписку") ||

                /*text.equals("1 месяц - 299₽") ||*/ /* меняем на @Value*/
                text.equals("1 месяц - " + this.monthlyPrice + this.currencySecond) ||

                /*text.equals("12 месяцев - 2490₽") ||*/ /* меняем на @Value*/
                text.equals("12 месяцев - " + this.yearlyPrice + this.currencySecond) ||

                text.equals("🧹 Очистить все") ||
                text.equals("🔙 Назад") ||
                text.equals("🏠 Главное меню") ||
                text.equals("⏰ Автопоиск") ||
                text.equals("🔔 Включить автопоиск") ||
                text.equals("🔕 Выключить автопоиск") ||
                text.equals("❌ Выйти") ||
                /* ДОБАВЛЯЕМ КНОПКИ ИНТЕРВАЛОВ В КОМАНДЫ МЕНЮ*/
                text.equals("30 мин") ||
                text.equals("60 мин") ||
                text.equals("120 мин") ||
                text.equals("✅ Начать поиск") ||
                text.equals("❌ Отмена") ||
                text.equals("📋 Информация") ||        /* ← ДОБАВЛЯЕМ*/
                text.equals("📞 Контакты") ||         /* ← ДОБАВЛЯЕМ*/
                text.equals("⚙️ Сменить данные Profi_ru") ||  /* ← ДОБАВЬ ЭТУ СТРОКУ*/
                text.startsWith("✏️ Ключ ");
    }

    private void handleCheckPaymentCommand(Long chatId) {
        stateManager.setUserState(chatId, UserStateManager.STATE_WAITING_PAYMENT_ID);
        telegramService.sendMessage(chatId, "Введите ID платежа из ЮKassa:");
    }

    private boolean isUserAuthorized(Long chatId) {
        String state = stateManager.getUserState(chatId);
        User user = userService.findByTelegramChatId(chatId);

        return (UserStateManager.STATE_AUTHORIZED_MAIN.equals(state) ||
                state.startsWith("WAITING_FOR_KEYWORD") ||
                UserStateManager.STATE_AUTO_SEARCH.equals(state) ||
                UserStateManager.STATE_WAITING_INTERVAL.equals(state) ||
                UserStateManager.STATE_AUTHORIZED_KEYWORDS.equals(state) ||
                UserStateManager.STATE_SUBSCRIPTION_MENU.equals(state) ||
                UserStateManager.STATE_SEARCH_IN_PROGRESS.equals(state) ||

                /* ДОБАВЛЯЕМ СОСТОЯНИЯ ВВОДА ПОИСКА:*/
                UserStateManager.STATE_WAITING_SEARCH_QUERY.equals(state) ||
                UserStateManager.STATE_WAITING_SEARCH_CONFIRMATION.equals(state)

                 ) && user != null;
    }

    private boolean isFreeCommand(String text) {
        return List.of(

                /*"💳 Оплатить подписку", "1 месяц - 299₽", "12 месяцев - 2490₽",*/ /* меняем на @Value*/
                "💳 Оплатить подписку", "1 месяц - " + this.monthlyPrice + this.currencySecond,
                "12 месяцев - " + this.yearlyPrice + this.currencySecond,

                "✅ Проверить оплату", "🔙 Назад", "🏠 Старт",
                "📝 Подключить_Profi_ru", "🔑 Войти", "❌ Выйти"
        ).contains(text);
    }

    private void sendWelcomeMenu(Long chatId) {
        telegramService.sendMessage(menuFactory.createWelcomeMenu(chatId));
    }

    private void sendMainMenu(Long chatId) {
        telegramService.sendMessage(menuFactory.createMainMenu(chatId));
    }

    /* ДОБАВЛЯЕМ ТОЛЬКО ЭТОТ ПЕРЕГРУЖЕННЫЙ МЕТОД:*/
    private void sendMainMenu(Long chatId, boolean afterSearch) {
        telegramService.sendMessage(menuFactory.createMainMenu(chatId, afterSearch));
    }

    private void sendSubscriptionMenu(Long chatId) {
        telegramService.sendMessage(menuFactory.createSubscriptionMenu(chatId));
    }

    @Override
    public void handleError(Update update, Exception exception) {
        log.error("Bot error processing update: {}", exception.getMessage());
        if (update.hasMessage()) {
            telegramService.sendMessage(update.getMessage().getChatId(), "⚠️ Произошла системная ошибка. Попробуйте позже.");
        }
    }

    @Override
    public void shutdown() {
        autoSearchService.shutdown();
        parser.close();
    }

    private void sendInfoMenu(Long chatId) {
        telegramService.sendMessage(menuFactory.createInfoMenu(chatId));
    }

    private void sendContactsMenu(Long chatId) {
        telegramService.sendMessage(menuFactory.createContactsMenu(chatId));
    }

}



