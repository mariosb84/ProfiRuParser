package org.example.profiruparser.bot.handlers;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

public interface PaymentHandler {
    void handleSubscriptionPayment(Long chatId, String plan);
    void handlePaymentCheck(Long chatId, String paymentId);
    void handlePaymentCheckCallback(CallbackQuery callbackQuery, String paymentId);
    void checkAutoPayment(Long chatId);
}

