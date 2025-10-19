package org.example.profiruparser.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.PaymentCreateRequest;
import org.example.profiruparser.domain.dto.PaymentCreateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class YooKassaClient {

    private final RestTemplate restTemplate;

    @Value("${yookassa.shop-id:}")
    private String shopId;

    @Value("${yookassa.secret-key:}")
    private String secretKey;

    @Value("${yookassa.test-mode:true}")
    private boolean testMode;

    private static final String API_URL = "https://api.yookassa.ru/v3/";
    private static final String TEST_API_URL = "https://api.yookassa.ru/v3/";

    public PaymentCreateResponse createPayment(PaymentCreateRequest request) {
        try {
            String url = (testMode ? TEST_API_URL : API_URL) + "payments";

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentCreateRequest> entity = new HttpEntity<>(request, headers);

            /* Логируем запрос*/
            log.info("Sending payment request to YooKassa: {}", request);

            /* Получаем сырой ответ для анализа*/
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            /* Логируем ПОЛНЫЙ ответ от ЮKassa*/
            log.info("=== YooKassa RAW Response ===");
            log.info("Status: {}", rawResponse.getStatusCode());
            log.info("Headers: {}", rawResponse.getHeaders());
            log.info("Body: {}", rawResponse.getBody());
            log.info("=== End YooKassa Response ===");

            /* Парсим JSON вручную чтобы увидеть структуру*/
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            /* Сначала парсим в Map чтобы увидеть все поля*/
            Map<String, Object> responseMap = mapper.readValue(rawResponse.getBody(), Map.class);
            log.info("Response as Map: {}", responseMap);

            /* Затем парсим в наш DTO*/
            PaymentCreateResponse response = mapper.readValue(rawResponse.getBody(), PaymentCreateResponse.class);

            log.info("Parsed response - ID: {}, Status: {}, Confirmation: {}",
                    response.getId(), response.getStatus(), response.getConfirmation());

            return response;

        } catch (Exception e) {
            log.error("Error creating payment: {}", e.getMessage());
            throw new RuntimeException("Payment creation failed", e);
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.set("Idempotence-Key", java.util.UUID.randomUUID().toString());
        return headers;
    }

    public PaymentCreateResponse getPayment(String paymentId) {
        String url = (testMode ? TEST_API_URL : API_URL) + "payments/" + paymentId;
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<PaymentCreateResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, PaymentCreateResponse.class);

        return response.getBody();
    }

}
