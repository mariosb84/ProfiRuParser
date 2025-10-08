package org.example.profiruparser.service;

import lombok.AllArgsConstructor;
import org.example.profiruparser.domain.dto.UserDto;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import org.example.profiruparser.domain.model.Role;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.example.profiruparser.domain.model.Role.ROLE_USER;

@Service
@AllArgsConstructor
public class UserServiceData implements UserService, UserDetailsService {

    private final BCryptPasswordEncoder encoder;

    private final UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(UserServiceData.class);


    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Создание пользователя
     *
     * @return созданный пользователь
     */
    @Override
    public Optional<User> add(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            /* Заменить на свои исключения*/
            throw new RuntimeException("Пользователь с таким именем уже существует");
        }

        /*УБИРАЕМ ПОКА ПРОВЕРКУ НА ДУБЛИРОВАНИЕ ПОЧТЫ И ТЕЛЕФОНА У ЮЗЕРОВ*/

        /*if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Пользователь с таким email уже существует");
        }
        if (userRepository.existsByPhone(user.getPhone())) {
            throw new RuntimeException("Пользователь с таким номером телефона уже существует");
        }*/
        user.setRoles(List.of(ROLE_USER));
        return Optional.ofNullable(save(user));
    }

    @Override
    public boolean update(User user) {
        userRepository.save(user);
        return userRepository.findById(user.getId()).isPresent();

    }

    @Override
    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    @Override
    public boolean delete(User user) {
        userRepository.delete(user);
        return userRepository.findById(user.getId()).isEmpty();
    }

    @Override
    public boolean updatePatch(UserDto userDto) {
        var person = userRepository.findById(userDto.getId());
        if (person.isPresent()) {
            User result = person.get();
            result.setPassword(encoder.encode(userDto.getPassword()));
             this.add(result);
             return true;
        }
        return false;
    }

    /**
     * Получение пользователя по имени пользователя
     *
     * @return пользователь
     */
    @Override
    public User findUserByUsername(String username) {
       /* return userRepository.findUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));*/
        return userRepository.findUserByUsername(username).orElse(null);
    }

    /**
     * Сохранение пользователя
     *
     * @return сохраненный пользователь
     */
    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public List<User> findUserByUsernameContains(String username) {
        return userRepository.findAllUsersByUsernameContaining(username);
    }

    @Override
    public Optional<User> setRoleOperator(long id) {
        Optional<User> user = findById(id);
        if (user.isPresent() && user.get().getRoles().contains(Role.ROLE_USER)
                && !(user.get().getRoles().contains(Role.ROLE_OPERATOR))) {
            List<Role> roles = user.get().getRoles();
            roles.add(Role.ROLE_OPERATOR);
            user.get().setRoles(roles);
            return user;
        }
        return Optional.empty();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByUsername(username).orElseThrow();
        if (user == null) {
            throw new UsernameNotFoundException(username);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), emptyList());
    }

    /**
     * Получение пользователя по имени пользователя
     * <p>
     * Нужен для Spring Security
     *
     * @return пользователь
     */
    public UserDetailsService userDetailsService() {
        return this::findUserByUsername;
    }

    /**
     * Получение текущего пользователя
     *
     * @return текущий пользователь
     */
    @Override
    public User getCurrentUser() {
        /* Получение имени пользователя из контекста Spring Security*/
        return findUserByUsername(SecurityContextHolder.
                getContext().getAuthentication().getName());
    }

    /**
     * Обновление у User -а только одного поля: subscriptionEndDate
     * <p>
     */
    @Transactional
    public User updateUserSubscriptionEndDate(String userName, LocalDateTime subscriptionEndDate) {
        logger.debug("Transaction started for user: {}", userName);
        User user = findUserByUsername(userName);
        if(user != null) {
            userRepository.updateSubscriptionEndDate(user.getId(), subscriptionEndDate);
            logger.debug("Transaction completed for user: {}", userName);
            return  user;
        }
        return  null;
    }

    public User findByTelegramChatId(Long chatId) {
        return userRepository.findByTelegramChatId(chatId);
    }

    public void updateTelegramChatId(String username, Long chatId) {
        User user = userRepository.findUserByUsername(username).get();
        user.setTelegramChatId(chatId);
        userRepository.save(user);
    }

}
