package org.example.profiruparser.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.keyboards.MenuFactory;
import org.example.profiruparser.bot.service.TelegramService;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.PaymentService;
import org.example.profiruparser.service.SubscriptionService;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentHandlerImpl implements PaymentHandler {

    @Value("${paymentUrl}")
    private String paymentUrl;

    @Value("${app.subscription.monthly.price}")
    private String monthlyPrice;

    @Value("${app.subscription.yearly.price}")
    private String yearlyPrice;

    @Value("${amountMonthly}")
    private String amountMonthly;

    @Value("${amountYearly}")
    private String amountYearly;

    @Value("${currency}")
    private String currency;

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final UserServiceData userService;
    private final MenuFactory menuFactory;
    private final TelegramService telegramService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void handleSubscriptionPayment(Long chatId, String plan) {
        executor.submit(() -> {
            try {
                User user = userService.findByTelegramChatId(chatId);
                if (user == null) {
                    telegramService.sendMessage(chatId, "❌ Пользователь не найден");
                    return;
                }

                PaymentService.SubscriptionPlan subscriptionPlan =
                        "MONTHLY".equals(plan) ?
                                PaymentService.SubscriptionPlan.MONTHLY :
                                PaymentService.SubscriptionPlan.YEARLY;

                var paymentResponse = paymentService.createPayment(chatId, subscriptionPlan);

                if (paymentResponse != null && paymentResponse.getId() != null) {
                    savePaymentId(chatId, paymentResponse.getId());

                    /*String paymentUrl = "https://yoomoney.ru/checkout/payments/v2/contract?orderId=" + paymentResponse.getId();*/ /*меняем на @Value*/
                    String paymentUrl = this.paymentUrl + paymentResponse.getId();

                    String messageText = "💳 *Оплата подписки*\n\n" +
                            /*"✅ Сумма: " + (subscriptionPlan == PaymentService.SubscriptionPlan.MONTHLY ? "299" : "2490") + " ₽\n" +*/ /* меняем на @Value*/
                            "✅ Сумма: " + (subscriptionPlan == PaymentService.SubscriptionPlan.MONTHLY ? this.monthlyPrice : this.yearlyPrice) + this.currency + " \n" +
                            "📝 Описание: " + subscriptionPlan.getDescription() + "\n\n" +
                            "🔗 Ссылка для оплаты:\n" +
                            paymentUrl + "\n\n" +
                            "После успешной оплаты подписка активируется автоматически " +
                            "втечение 59 секунд!";

                    telegramService.sendMessage(chatId, messageText);
                    telegramService.sendMessage(chatId, "🆔 ID платежа: `" + paymentResponse.getId() + "`");

                } else {
                    telegramService.sendMessage(chatId, "❌ Ошибка создания платежа");
                }

            } catch (Exception e) {
                log.error("Payment error for chatId: {}", chatId, e);
                telegramService.sendMessage(chatId, "❌ Ошибка при создании платежа: " + e.getMessage());
            }
        });
    }

    @Override
    public void handlePaymentCheck(Long chatId, String paymentId) {
        executor.submit(() -> {
            try {
                var payment = paymentService.getPaymentStatus(paymentId);

                if (payment != null && "succeeded".equals(payment.getStatus())) {
                    User user = userService.findByTelegramChatId(chatId);
                    if (user != null) {
                        String amount = payment.getAmount().getValue();

                       /* int days = "2490.00".equals(amount) ? 365 : 30;*/ /* меняем на @Value*/
                        int days = this.amountYearly.equals(amount) ? 365 : 30;

                        boolean success = subscriptionService.activateSubscription(user.getUsername(), days);

                        if (success) {
                            telegramService.sendMessage(chatId, "✅ Подписка активирована на " + days + " дней!");
                            telegramService.sendMessage(menuFactory.createMainMenu(chatId));
                        } else {
                            telegramService.sendMessage(chatId, "❌ Ошибка активации подписки");
                        }
                    }
                } else {
                    telegramService.sendMessage(chatId, "❌ Платеж не найден или не завершен");
                }
            } catch (Exception e) {
                log.error("Error checking payment: {}", e.getMessage());
                telegramService.sendMessage(chatId, "❌ Ошибка проверки платежа");
            }
        });
    }

    @Override
    public void handlePaymentCheckCallback(CallbackQuery callbackQuery, String paymentId) {
        executor.submit(() -> {
            try {
                var paymentStatus = paymentService.getPaymentStatus(paymentId);
                Long chatId = callbackQuery.getMessage().getChatId();

                if ("succeeded".equals(paymentStatus.getStatus())) {
                    answerCallback(callbackQuery, "✅ Платеж успешно завершен!");
                    telegramService.sendMessage(chatId, "🎉 Платеж подтвержден! Подписка активирована.");
                    telegramService.sendMessage(menuFactory.createMainMenu(chatId));
                } else if ("pending".equals(paymentStatus.getStatus())) {
                    answerCallback(callbackQuery, "⏳ Платеж еще обрабатывается...");
                } else {
                    answerCallback(callbackQuery, "❌ Платеж не прошел");
                }
            } catch (Exception e) {
                log.error("Error checking payment callback: {}", e.getMessage());
                answerCallback(callbackQuery, "❌ Ошибка проверки статуса");
            }
        });
    }

    @Override
    public void checkAutoPayment(Long chatId) {
        executor.submit(() -> {
            try {
                User user = userService.findByTelegramChatId(chatId);
                if (user == null || subscriptionService.isSubscriptionActive(user.getUsername())) {
                    return;
                }

                telegramService.sendMessage(chatId, "💡 Если вы уже оплатили подписку, нажмите '✅ Проверить оплату' в меню оплаты");

            } catch (Exception e) {
                log.error("Auto payment check error: {}", e.getMessage());
            }
        });
    }

    private void savePaymentId(Long chatId, String paymentId) {
        try {
            User user = userService.findByTelegramChatId(chatId);
            if (user != null) {
                log.info("Payment created - ChatId: {}, User: {}, PaymentId: {}",
                        chatId, user.getUsername(), paymentId);
            }
        } catch (Exception e) {
            log.error("Error saving payment ID: {}", e.getMessage());
        }
    }

    private void answerCallback(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        telegramService.answerCallback(answer);
    }

}
