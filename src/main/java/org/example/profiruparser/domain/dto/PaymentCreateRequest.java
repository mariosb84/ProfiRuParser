package org.example.profiruparser.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class PaymentCreateRequest {
    private Amount amount; /* Изменяем с BigDecimal на Amount*/
    private String currency;
    private String description;
    private Map<String, String> metadata;
    private Confirmation confirmation;
    private boolean capture = true;

    @Data
    public static class Amount {
        private String value; /* Строка в формате "299.00"*/
        private String currency;
    }

    @Data
    public static class Confirmation {
        private String type = "redirect";
        private String returnUrl;
    }

}
