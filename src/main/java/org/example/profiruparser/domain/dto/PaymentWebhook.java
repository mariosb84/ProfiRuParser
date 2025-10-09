package org.example.profiruparser.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class PaymentWebhook {
    private String type;
    private String event;
    private PaymentObject object;

    @Data
    public static class PaymentObject {
        private String id;
        private String status;
        private String description;
        private Amount amount;
        private Map<String, String> metadata;
        private boolean paid;
        private String capturedAt;
    }

    @Data
    public static class Amount {
        private String value;
        private String currency;
    }

}
