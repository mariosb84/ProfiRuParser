package org.example.profiruparser.service;

import lombok.RequiredArgsConstructor;
import org.example.profiruparser.repository.SeenOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeenOrderService {

    private final SeenOrderRepository seenOrderRepository;

    public boolean hasUserSeenOrder(Long userId, String orderId) {
        return seenOrderRepository.existsByUserIdAndOrderId(userId, orderId);
    }

    public Set<String> getSeenOrderIds(Long userId) {
        return seenOrderRepository.findSeenOrderIdsByUserId(userId);
    }

    @Transactional
    public void markOrdersAsSeen(Long userId, List<String> orderIds) {
        for (String orderId : orderIds) {
            seenOrderRepository.saveSeenOrder(userId, orderId);
        }
    }

    /* Очистка раз в месяц*/
    @Scheduled(cron = "0 0 1 1 * ?") /* 1 число каждого месяца в 1:00*/
    @Transactional
    public void cleanupOldSeenOrders() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        seenOrderRepository.deleteOldSeenOrders(oneMonthAgo);
        System.out.println("Cleaned up old seen orders");
    }

}