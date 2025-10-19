package org.example.profiruparser.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.model.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAutoCheckService implements PaymentAutoCheckManager {

    private final YooKassaClient yooKassaClient; // ← ВМЕСТО PaymentService
    private final SubscriptionService subscriptionService;
    private final UserServiceData userService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> activeChecks = new ConcurrentHashMap<>();
    private final Map<String, Long> paymentChatIds = new ConcurrentHashMap<>();

    @Override
    public void startAutoCheck(String paymentId, Long chatId) {
        log.info("Starting auto-check for payment: {}, chatId: {}", paymentId, chatId);

        paymentChatIds.put(paymentId, chatId);

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                checkPaymentStatus(paymentId);
            } catch (Exception e) {
                log.error("Error in auto-check for payment: {}", paymentId, e);
            }
        }, 30, 60, TimeUnit.SECONDS);

        activeChecks.put(paymentId, task);

        scheduler.schedule(() -> {
            if (activeChecks.containsKey(paymentId)) {
                log.info("Auto-check expired for payment: {} (24 hours passed)", paymentId);
                stopAutoCheck(paymentId);
            }
        }, 24, TimeUnit.HOURS);
    }

    private void checkPaymentStatus(String paymentId) {
        try {
            /* ИСПОЛЬЗУЕМ YooKassaClient напрямую*/
            var paymentStatus = yooKassaClient.getPayment(paymentId);
            log.debug("Auto-check payment {} status: {}", paymentId, paymentStatus.getStatus());

            if ("succeeded".equals(paymentStatus.getStatus())) {
                activateSubscriptionFromPayment(paymentId);
                stopAutoCheck(paymentId);
            } else if ("canceled".equals(paymentStatus.getStatus())) {
                log.info("Payment canceled: {}", paymentId);
                stopAutoCheck(paymentId);
            }

        } catch (Exception e) {
            log.error("Error checking payment status: {}", paymentId, e);
        }
    }

    private void activateSubscriptionFromPayment(String paymentId) {
        try {
            Long chatId = paymentChatIds.get(paymentId);
            if (chatId == null) return;

            User user = userService.findByTelegramChatId(chatId);
            if (user == null) return;

            /* Получаем информацию о платеже напрямую через YooKassaClient*/
            var paymentInfo = yooKassaClient.getPayment(paymentId);
            String amount = paymentInfo.getAmount().getValue();

            PaymentService.SubscriptionPlan plan = "2490.00".equals(amount)
                    ? PaymentService.SubscriptionPlan.YEARLY
                    : PaymentService.SubscriptionPlan.MONTHLY;

            boolean success = subscriptionService.activateSubscriptionViaPayment(user.getUsername(), plan);

            if (success) {
                log.info("Subscription activated via auto-check for user: {}, payment: {}",
                        user.getUsername(), paymentId);
            } else {
                log.error("Failed to activate subscription via auto-check for user: {}",
                        user.getUsername());
            }

        } catch (Exception e) {
            log.error("Error activating subscription from payment: {}", paymentId, e);
        } finally {
            paymentChatIds.remove(paymentId);
        }
    }

    @Override
    public void stopAutoCheck(String paymentId) {
        ScheduledFuture<?> task = activeChecks.get(paymentId);
        if (task != null) {
            task.cancel(false);
            activeChecks.remove(paymentId);
            paymentChatIds.remove(paymentId);
            log.info("Auto-check stopped for payment: {}", paymentId);
        }
    }

    @Override
    public boolean isAutoCheckActive(String paymentId) {
        return activeChecks.containsKey(paymentId);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PaymentAutoCheckService");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}