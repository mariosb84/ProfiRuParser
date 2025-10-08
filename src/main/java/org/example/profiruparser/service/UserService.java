package org.example.profiruparser.service;




import org.example.profiruparser.domain.dto.UserDto;
import org.example.profiruparser.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

     List<User> findAll();

    Optional<User> add(User user);

   boolean update(User user);

     Optional<User> findById(long id);

    boolean delete(User user);

    boolean updatePatch(UserDto userDto);

    User findUserByUsername(String username);

    User save(User user);

    List<User> findUserByUsernameContains(String username);

    Optional<User> setRoleOperator(long id);

    User getCurrentUser();

}
