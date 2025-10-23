package org.example.profiruparser.service;

import lombok.RequiredArgsConstructor;
import org.example.profiruparser.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    @Value("${app.trial.period-days}")
    private int trialPeriodDays;

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);
    private final UserServiceData userService;

  /*  @Transactional
    public boolean activateSubscription(String username, int days) {
        logger.debug("Activating subscription for user: {}, days: {}", username, days);

        User user = userService.findUserByUsername(username);
        if (user == null) {
            logger.error("User not found: {}", username);
            return false;
        }

        LocalDateTime currentEndDate = user.getSubscriptionEndDate();
        LocalDateTime newEndDate;

        *//* ЕСЛИ ПОДПИСКА УЖЕ АКТИВНА - ПРОДЛЕВАЕМ ОТ ТЕКУЩЕЙ ДАТЫ ОКОНЧАНИЯ*//*
        if (currentEndDate != null && currentEndDate.isAfter(LocalDateTime.now())) {
            newEndDate = currentEndDate.plusDays(days);
            logger.debug("Extending subscription from {} to {}", currentEndDate, newEndDate);
        } else {
            *//* ЕСЛИ ПОДПИСКИ НЕТ ИЛИ ОНА ИСТЕКЛА - НАЧИНАЕМ С ТЕКУЩЕЙ ДАТЫ*//*
            newEndDate = LocalDateTime.now().plusDays(days);
            logger.debug("Starting new subscription until: {}", newEndDate);
        }

        User updatedUser = userService.updateUserSubscriptionEndDate(username, newEndDate);

        if (updatedUser != null) {
            logger.info("Subscription activated for user: {} until {}", username, newEndDate);
            return true;
        }

        logger.error("Failed to activate subscription for user: {}", username);
        return false;
    }*/

    @Transactional
    public boolean activateSubscription(String username, int days) {
        logger.debug("Activating subscription for user: {}, days: {}", username, days);

        User user = userService.findUserByUsername(username);
        if (user == null) {
            logger.error("User not found: {}", username);
            return false;
        }

        LocalDateTime currentEndDate = user.getSubscriptionEndDate();
        LocalDateTime newEndDate;

        /* ЕСЛИ АКТИВИРУЕМ ПЛАТНУЮ ПОДПИСКУ - СНИМАЕМ TRIAL ФЛАГ*/
        user.setTrialUsed(false);

        /* ЕСЛИ ПОДПИСКА УЖЕ АКТИВНА - ПРОДЛЕВАЕМ ОТ ТЕКУЩЕЙ ДАТЫ ОКОНЧАНИЯ*/
        if (currentEndDate != null && currentEndDate.isAfter(LocalDateTime.now())) {
            newEndDate = currentEndDate.plusDays(days);
            logger.debug("Extending subscription from {} to {}", currentEndDate, newEndDate);
        } else {
            /* ЕСЛИ ПОДПИСКИ НЕТ ИЛИ ОНА ИСТЕКЛА - НАЧИНАЕМ С ТЕКУЩЕЙ ДАТЫ*/
            newEndDate = LocalDateTime.now().plusDays(days);
            logger.debug("Starting new subscription until: {}", newEndDate);
        }

        user.setSubscriptionEndDate(newEndDate);
        User updatedUser = userService.save(user); /* ← ИСПРАВИТЬ НА save*/

        if (updatedUser != null) {
            logger.info("Subscription activated for user: {} until {}", username, newEndDate);
            return true;
        }

        logger.error("Failed to activate subscription for user: {}", username);
        return false;
    }

    /* НОВЫЙ МЕТОД: Активация подписки через платеж (для webhook)*/
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

    /* НОВЫЙ МЕТОД: Получить оставшееся время подписки в днях*/
    public long getDaysRemaining(String username) {
        User user = userService.findUserByUsername(username);
        if (user == null || user.getSubscriptionEndDate() == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = user.getSubscriptionEndDate();

        if (endDate.isBefore(now)) {
            return 0; /* Подписка истекла*/
        }

        return ChronoUnit.DAYS.between(now, endDate);
    }

    /* НОВЫЙ МЕТОД: Получить статус подписки для отображения*/
    public String getSubscriptionStatus(String username) {
        if (!isSubscriptionActive(username)) {
            return "❌ Подписка не активна";
        }

        if (isTrialSubscription(username)) {
            long daysRemaining = getDaysRemaining(username);
            LocalDateTime endDate = getSubscriptionEndDate(username);
            return "🆓 *Пробный период* до: " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) +
                    " (" + daysRemaining + " д.)";
        } else {
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
                        endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                        " (" + daysRemaining + " д.)";
            }
        }
    }

    /* ПРОБНАЯ ПОДПИСКА :*/
    @Transactional
    public boolean activateTrialSubscription(String username) {
        User user = userService.findUserByUsername(username);
        if (user == null) {
            logger.error("User not found for trial: {}", username);
            return false;
        }

        /* Проверяем, не использовал ли уже пробный период*/
        if (user.getTrialUsed() != null && user.getTrialUsed()) {
            logger.info("Trial already used for user: {}", username);
            return false;
        }

        /* Активируем пробный период*/
        LocalDateTime trialEndDate = LocalDateTime.now().plusDays(trialPeriodDays);
        user.setSubscriptionEndDate(trialEndDate);
        user.setTrialUsed(true);
        userService.save(user);

        logger.info("Trial subscription activated for user: {} until {}", username, trialEndDate);
        return true;
    }

    public boolean isTrialSubscription(String username) {
        User user = userService.findUserByUsername(username);
        if (user == null || user.getSubscriptionEndDate() == null) {
            return false;
        }

        /* Считаем пробной подписку, если использован trial и подписка активна*/
        return user.getTrialUsed() != null &&
                user.getTrialUsed() &&
                user.isSubscriptionActive();
    }

}

