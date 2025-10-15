package org.example.profiruparser.service;

public interface PaymentAutoCheckManager {
    void startAutoCheck(String paymentId, Long chatId);
    void stopAutoCheck(String paymentId);
    boolean isAutoCheckActive(String paymentId);
}