package org.example.profiruparser.service;

import lombok.RequiredArgsConstructor;
import org.example.profiruparser.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final UserServiceData userService;

    @Transactional
    public boolean activateSubscription(String username, int days) {

        logger.debug("Transaction started for user: {}", username);

        System.out.println("=== ACTIVATE SUBSCRIPTION ===");
        System.out.println("Username: " + username);
        System.out.println("Days: " + days);

        LocalDateTime newDate = LocalDateTime.now().plusDays(days);

        User user = userService.updateUserSubscriptionEndDate(username, newDate);

        System.out.println("User found: " + (user != null));

        if (user != null) {
            System.out.println("Setting date to: " + newDate);
            System.out.println("Save result: " + user);
            System.out.println("Saved date: " + user.getSubscriptionEndDate());

            logger.debug("Transaction completed for user: {}", username);

            return true;
        }
        return false;
    }

    public boolean isSubscriptionActive(String username) {
        User user = userService.findUserByUsername(username);
        boolean active = user != null && user.isSubscriptionActive();
        System.out.println("Subscription active for " + username + ": " + active);
        return active;
    }

    public LocalDateTime getSubscriptionEndDate(String username) {
        User user = userService.findUserByUsername(username);
        LocalDateTime date = user != null ? user.getSubscriptionEndDate() : null;
        System.out.println("Subscription end date for " + username + ": " + date);
        return date;
    }

}
