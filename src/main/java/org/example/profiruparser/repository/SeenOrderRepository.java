package org.example.profiruparser.repository;

import org.example.profiruparser.domain.model.SeenOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Set;

@Repository
public interface SeenOrderRepository extends JpaRepository<SeenOrder, Long> {

    /* Проверить, видел ли пользователь заказ*/
    boolean existsByUserIdAndOrderId(Long userId, String orderId);

    /* Найти все ID заказов, которые пользователь уже видел*/
    @Query("SELECT so.orderId FROM SeenOrder so WHERE so.userId = :userId")
    Set<String> findSeenOrderIdsByUserId(@Param("userId") Long userId);

    /* Сохранить несколько просмотренных заказов*/
    @Modifying
    @Query(value = "INSERT INTO seen_orders (user_id, order_id, seen_at) VALUES (:userId, :orderId, NOW()) " +
            "ON CONFLICT (user_id, order_id) DO NOTHING", nativeQuery = true)
    void saveSeenOrder(@Param("userId") Long userId, @Param("orderId") String orderId);

    /* Очистка старых записей (раз в месяц)*/
    @Modifying
    @Query("DELETE FROM SeenOrder so WHERE so.seenAt < :cutoffDate")
    void deleteOldSeenOrders(@Param("cutoffDate") LocalDateTime cutoffDate);

    /* Количество просмотренных заказов пользователя*/
    long countByUserId(Long userId);

}
