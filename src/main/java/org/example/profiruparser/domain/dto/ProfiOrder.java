package org.example.profiruparser.domain.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProfiOrder {

    @EqualsAndHashCode.Include
    private String id;

    private String title;
    private String price;
    private String description;
    private String creationTime;
    private String clientName;
    private String orderNumber;

}
