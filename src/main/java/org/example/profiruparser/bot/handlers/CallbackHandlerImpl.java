package org.example.profiruparser.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.service.SearchService;
import org.example.profiruparser.bot.service.TelegramService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackHandlerImpl implements CallbackHandler {

    private final SearchService searchService;
    private final TelegramService telegramService;
    private final PaymentHandler paymentHandler;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.debug("Handling callback - ChatId: {}, Data: {}", chatId, data);

        try {
            if (data.startsWith("respond_")) {
                handleRespondCallback(callbackQuery, data, chatId);
            } else if (data.startsWith("check_payment_")) {
                handlePaymentCheckCallback(callbackQuery, data);
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage());
            /* Не отвечаем на колбэк если уже прошло много времени*/
        }
    }

    private void handleRespondCallback(CallbackQuery callbackQuery, String data, Long chatId) {
        /* СРАЗУ отвечаем на колбэк чтобы избежать "query is too old"*/
        answerCallback(callbackQuery, "⏳ Отправляем отклик...");

        executor.submit(() -> {
            try {
                String orderId = data.substring("respond_".length());
                boolean success = searchService.handleRespondToOrder(chatId, orderId);

                /* Результат отправляем ОТДЕЛЬНЫМ сообщением*/
                if (success) {
                    telegramService.sendMessage(chatId, "✅ Отклик на заказ #" + orderId + " отправлен!");
                } else {
                    telegramService.sendMessage(chatId, "❌ Не удалось отправить отклик на заказ #" + orderId);
                }
            } catch (Exception e) {
                log.error("Error responding to order: {}", e.getMessage());
                telegramService.sendMessage(chatId, "❌ Ошибка при отправке отклика");
            }
        });
    }

    private void handlePaymentCheckCallback(CallbackQuery callbackQuery, String data) {
        /* Для платежей тоже сразу отвечаем*/
        answerCallback(callbackQuery, "⏳ Проверяем платеж...");

        executor.submit(() -> {
            try {
                String paymentId = data.substring("check_payment_".length());
                paymentHandler.handlePaymentCheckCallback(callbackQuery, paymentId);
            } catch (Exception e) {
                log.error("Error handling payment callback: {}", e.getMessage());
                telegramService.sendMessage(callbackQuery.getMessage().getChatId(), "❌ Ошибка проверки платежа");
            }
        });
    }

    private void answerCallback(CallbackQuery callbackQuery, String text) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText(text);
            answer.setShowAlert(false); /* Всплывающее уведомление*/
            telegramService.answerCallback(answer);
        } catch (Exception e) {
            log.warn("Failed to answer callback (probably too old): {}", e.getMessage());
            /* Игнорируем ошибку "query is too old"*/
        }
    }

}