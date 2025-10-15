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
                handleRespondCallback(callbackQuery, data);
            } else if (data.startsWith("check_payment_")) {
                handlePaymentCheckCallback(callbackQuery, data);
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage());
            answerCallback(callbackQuery, "❌ Ошибка обработки запроса");
        }
    }

    private void handleRespondCallback(CallbackQuery callbackQuery, String data) {
        executor.submit(() -> {
            try {
                String orderId = data.substring("respond_".length());
                boolean success = searchService.handleRespondToOrder(orderId);
                answerCallback(callbackQuery, success ? "✅ Отклик отправлен" : "❌ Ошибка отправки");
            } catch (Exception e) {
                log.error("Error responding to order: {}", e.getMessage());
                answerCallback(callbackQuery, "❌ Ошибка");
            }
        });
    }

    private void handlePaymentCheckCallback(CallbackQuery callbackQuery, String data) {
        executor.submit(() -> {
            try {
                String paymentId = data.substring("check_payment_".length());
                paymentHandler.handlePaymentCheckCallback(callbackQuery, paymentId);
            } catch (Exception e) {
                log.error("Error handling payment callback: {}", e.getMessage());
                answerCallback(callbackQuery, "❌ Ошибка проверки платежа");
            }
        });
    }

    private void answerCallback(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        telegramService.answerCallback(answer);
    }

}