package org.example.profiruparser.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.PaymentWebhook;
import org.example.profiruparser.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/webhook/yookassa")
    public ResponseEntity<?> handleYooKassaWebhook(@RequestBody PaymentWebhook webhook) {
        log.info("Processing YooKassa webhook: {}", webhook.getEvent());

        try {
            paymentService.handleWebhook(webhook);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

}
