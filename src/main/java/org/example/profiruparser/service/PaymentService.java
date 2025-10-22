package org.example.profiruparser.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.PaymentCreateRequest;
import org.example.profiruparser.domain.dto.PaymentCreateResponse;
import org.example.profiruparser.domain.dto.PaymentWebhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final YooKassaClient yooKassaClient;
    private final SubscriptionService subscriptionService;
    private final UserServiceData userService;
    private final PaymentAutoCheckManager autoCheckManager; /* ← ИНТЕРФЕЙС вместо реализации*/

    @Value("${app.payment.return-url}")
    private String returnUrl;

    @Value("${amountMonthly}")
    private String amountMonthly;

    @Value("${amountYearly}")
    private String amountYearly;

    @Value("${amountSetCurrency}")
    private String amountSetCurrency;

    @Getter
    public enum SubscriptionPlan {
        /*MONTHLY("299.00", "Подписка на 1 месяц"),
        YEARLY("2490.00", "Подписка на 12 месяцев");*/ /* меняем на @Value*/

        MONTHLY("Подписка на 1 месяц"),
        YEARLY("Подписка на 12 месяцев");

        private final String description;

        SubscriptionPlan(String description) {
            this.description = description;
        }
    }

    /* НОВЫЙ МЕТОД: получение цены из конфига*/
    public String getPrice(SubscriptionPlan plan) {
        return switch (plan) {
            case MONTHLY -> amountMonthly;
            case YEARLY -> amountYearly;
        };
    }

    /* НОВЫЙ МЕТОД: получение BigDecimal цены*/
    public BigDecimal getPriceAsBigDecimal(SubscriptionPlan plan) {
        return new BigDecimal(getPrice(plan));
    }

    public PaymentCreateResponse createPayment(Long chatId, SubscriptionPlan plan) {
        try {
            PaymentCreateRequest request = new PaymentCreateRequest();

            /* ИСПОЛЬЗУЕМ ЦЕНУ ИЗ КОНФИГА*/
            String price = getPrice(plan);

            /* Правильный формат amount для ЮKassa*/
            PaymentCreateRequest.Amount amount = new PaymentCreateRequest.Amount();
            amount.setValue(price);  /* значение из конфига*/

            /*amount.setCurrency("RUB");*/ /*меняем на @Value*/
            amount.setCurrency(this.amountSetCurrency);

            request.setAmount(amount);

            request.setDescription(plan.getDescription());

            /* Добавляем метаданные для вебхука*/
            Map<String, String> metadata = new HashMap<>();
            metadata.put("chatId", chatId.toString());
            metadata.put("plan", plan.name());
            var user = userService.findByTelegramChatId(chatId);
            if (user != null) {
                metadata.put("userId", user.getUsername());
            }
            request.setMetadata(metadata);

            /* Настройка подтверждения*/
            PaymentCreateRequest.Confirmation confirmation = new PaymentCreateRequest.Confirmation();
            /* Убираем return_url для Telegram бота - он не нужен*/
            /* confirmation.setReturnUrl(returnUrl);*/
            /* confirmation.setReturnUrl("https://yookassa.ru"); */  /* Просто сайт ЮKassa*/
            /*confirmation.setType("redirect");*/ /* Должен быть "redirect" для получения URL*/
            confirmation.setType("embedded"); /* Для встраивания в приложение*/
            request.setConfirmation(confirmation);
            /*request.setConfirmation(null);*/

            PaymentCreateResponse response = yooKassaClient.createPayment(request);
            log.info("Created payment for chatId: {}, plan: {}, amount: {}, paymentId: {}",
                    chatId, plan, price, response.getId());

            /* ЗАПУСКАЕМ АВТОМАТИЧЕСКУЮ ПРОВЕРКУ ← ДОБАВЛЯЕМ*/
            autoCheckManager.startAutoCheck(response.getId(), chatId);

            return response;

        } catch (Exception e) {
            log.error("Failed to create payment for chatId: {}, plan: {}", chatId, plan, e);
            throw new RuntimeException("Payment creation failed", e);
        }
    }

    public void handleWebhook(PaymentWebhook webhook) {
        log.info("Received webhook: {}", webhook);

        if ("payment.succeeded".equals(webhook.getEvent())) {
            PaymentWebhook.PaymentObject payment = webhook.getObject();

            if (payment.isPaid()) {
                Map<String, String> metadata = payment.getMetadata();
                String chatIdStr = metadata.get("chatId");
                String planStr = metadata.get("plan");

                if (chatIdStr != null && planStr != null) {
                    try {
                        Long chatId = Long.parseLong(chatIdStr);
                        SubscriptionPlan plan = SubscriptionPlan.valueOf(planStr);

                        /* ВЫЗЫВАЕМ МЕТОД ДЛЯ АКТИВАЦИИ ПОДПИСКИ*/
                        activateSubscription(chatId, plan);

                        log.info("Subscription activated via webhook: chatId={}, plan={}", chatId, plan);

                    } catch (Exception e) {
                        log.error("Error processing webhook: {}", e.getMessage());
                    }
                }
            }
        }
    }

    private void activateSubscription(Long chatId, SubscriptionPlan plan) {
        try {
            var user = userService.findByTelegramChatId(chatId);
            if (user == null) {
                log.error("User not found for chatId: {}", chatId);
                return;
            }

            /* ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД из SubscriptionService*/
            boolean success = subscriptionService.activateSubscriptionViaPayment(user.getUsername(), plan);

            if (success) {
                log.info("Subscription activated via payment for user: {}", user.getUsername());

               /*  Здесь можно добавить отправку уведомления пользователю
                 Но для этого нужен доступ к боту из PaymentService
                 Пока просто логируем*/

            } else {
                log.error("Failed to activate subscription for user: {}", user.getUsername());
            }

        } catch (Exception e) {
            log.error("Error activating subscription: {}", e.getMessage());
        }
    }

    public PaymentCreateResponse getPaymentStatus(String paymentId) {
        return yooKassaClient.getPayment(paymentId);
    }

}
