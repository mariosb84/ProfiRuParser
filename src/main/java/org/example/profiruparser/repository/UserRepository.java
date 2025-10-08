package org.example.profiruparser.repository;


import org.example.profiruparser.domain.model.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
    public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = "roles")
    @NonNull
    List<User> findAll();

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = "roles")
    List<User> findAllUsersByUsername(String username);

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = "roles")
    List<User> findAllUsersByUsernameContaining(String usernamePart);

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = "roles")
    Optional<User> findUserByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

   /* @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.yandexJson = :yandexJson WHERE u.id = :id")
    void updateYandexJson(@Param("id") Long id, @Param("yandexJson") String yandexJson);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.questionJson = :questionJson WHERE u.id = :id")
    int updateQuestionJson(@Param("id") Long id, @Param("questionJson") String questionJson);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.yandexToken = :yandexToken WHERE u.id = :id")
    int updateYandexToken(@Param("id") Long id, @Param("yandexToken") String yandexToken);*/

     @Modifying(clearAutomatically = true)
     @Transactional
     @Query("UPDATE User u SET u.subscriptionEndDate = :subscriptionEndDate WHERE u.id = :id")
     void updateSubscriptionEndDate(@Param("id") Long id,
                                   @Param("subscriptionEndDate") LocalDateTime subscriptionEndDate);


    User findByTelegramChatId(Long chatId);

}
