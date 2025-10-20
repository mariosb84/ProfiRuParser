package org.example.profiruparser.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seen_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(SeenOrderId.class) /* Составной ключ*/
public class SeenOrder {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    /* Связь с пользователем (опционально)*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}

/* Класс для составного ключа*/
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class SeenOrderId implements java.io.Serializable {
    private Long userId;
    private String orderId;

}