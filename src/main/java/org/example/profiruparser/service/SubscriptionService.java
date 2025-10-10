package org.example.profiruparser.service;

import lombok.RequiredArgsConstructor;
import org.example.profiruparser.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);
    private final UserServiceData userService;

    @Transactional
    public boolean activateSubscription(String username, int days) {
        logger.debug("Activating subscription for user: {}, days: {}", username, days);

        LocalDateTime newDate = LocalDateTime.now().plusDays(days);
        User user = userService.updateUserSubscriptionEndDate(username, newDate);

        if (user != null) {
            logger.info("Subscription activated for user: {} until {}", username, newDate);
            return true;
        }

        logger.error("Failed to activate subscription for user: {}", username);
        return false;
    }

    // НОВЫЙ МЕТОД: Активация подписки через платеж (для webhook)
    @Transactional
    public boolean activateSubscriptionViaPayment(String username, PaymentService.SubscriptionPlan plan) {
        int days = plan == PaymentService.SubscriptionPlan.MONTHLY ? 30 : 365;
        return activateSubscription(username, days);
    }

    public boolean isSubscriptionActive(String username) {
        User user = userService.findUserByUsername(username);
        boolean active = user != null && user.isSubscriptionActive();
        logger.debug("Subscription active for {}: {}", username, active);
        return active;
    }

    public LocalDateTime getSubscriptionEndDate(String username) {
        User user = userService.findUserByUsername(username);
        LocalDateTime date = user != null ? user.getSubscriptionEndDate() : null;
        logger.debug("Subscription end date for {}: {}", username, date);
        return date;
    }

    // НОВЫЙ МЕТОД: Получить оставшееся время подписки в днях
    public long getDaysRemaining(String username) {
        User user = userService.findUserByUsername(username);
        if (user == null || user.getSubscriptionEndDate() == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = user.getSubscriptionEndDate();

        if (endDate.isBefore(now)) {
            return 0; // Подписка истекла
        }

        return ChronoUnit.DAYS.between(now, endDate);
    }

    // НОВЫЙ МЕТОД: Получить статус подписки для отображения
    public String getSubscriptionStatus(String username) {
        if (!isSubscriptionActive(username)) {
            return "❌ Подписка не активна";
        }

        long daysRemaining = getDaysRemaining(username);
        LocalDateTime endDate = getSubscriptionEndDate(username);

        if (daysRemaining == 0) {
            return "⚠️ Подписка истекает сегодня";
        } else if (daysRemaining == 1) {
            return "⚠️ Подписка истекает завтра";
        } else if (daysRemaining <= 7) {
            return "✅ Подписка активна (осталось " + daysRemaining + " д.)";
        } else {
            return "✅ Подписка активна до: " +
                    endDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                    " (" + daysRemaining + " д.)";
        }
    }

}