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

    @Value("${app.payment.return-url:https://t.me/your_bot}")
    private String returnUrl;

    public enum SubscriptionPlan {
        MONTHLY("299.00", "Подписка на 1 месяц"),
        YEARLY("2490.00", "Подписка на 12 месяцев");

        private final String amount;
        @Getter
        private final String description;

        SubscriptionPlan(String amount, String description) {
            this.amount = amount;
            this.description = description;
        }

        public String getAmountValue() {
            return amount;
        }

        public BigDecimal getAmount() {
            return new BigDecimal(amount);
        }

    }

    public PaymentCreateResponse createPayment(Long chatId, SubscriptionPlan plan) {
        try {
            PaymentCreateRequest request = new PaymentCreateRequest();

            // Правильный формат amount для ЮKassa
            PaymentCreateRequest.Amount amount = new PaymentCreateRequest.Amount();
            amount.setValue(plan.getAmountValue()); // "299.00"
            amount.setCurrency("RUB");
            request.setAmount(amount);

            request.setDescription(plan.getDescription());

            // Добавляем метаданные для вебхука
            Map<String, String> metadata = new HashMap<>();
            metadata.put("chatId", chatId.toString());
            metadata.put("plan", plan.name());
            var user = userService.findByTelegramChatId(chatId);
            if (user != null) {
                metadata.put("userId", user.getUsername());
            }
            request.setMetadata(metadata);

            // Настройка подтверждения
            PaymentCreateRequest.Confirmation confirmation = new PaymentCreateRequest.Confirmation();
            // Убираем return_url для Telegram бота - он не нужен
            // confirmation.setReturnUrl(returnUrl);
           // confirmation.setReturnUrl("https://yookassa.ru"); // Просто сайт ЮKassa
            //confirmation.setType("redirect"); // Должен быть "redirect" для получения URL
            confirmation.setType("embedded"); // Для встраивания в приложение
            request.setConfirmation(confirmation);
            //request.setConfirmation(null);

            PaymentCreateResponse response = yooKassaClient.createPayment(request);
            log.info("Created payment for chatId: {}, plan: {}, paymentId: {}",
                    chatId, plan, response.getId());

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

                        // Активируем подписку
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

            int days = plan == SubscriptionPlan.MONTHLY ? 30 : 365;
            boolean success = subscriptionService.activateSubscription(user.getUsername(), days);

            if (success) {
                log.info("Subscription activated for user: {}", user.getUsername());
                // Здесь можно отправить уведомление в Telegram
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
