package org.example.profiruparser.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.profiruparser.domain.dto.UserDto;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.handlers.Operation;
import org.example.profiruparser.service.UserService;
import org.example.profiruparser.service.UserServiceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/person")
public class UserController {

    private final UserService persons;

    private final UserServiceData personsData;

    private final BCryptPasswordEncoder encoder;

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class.getSimpleName());

    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public List<User> findAll() {
        return this.persons.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable int id) {
        var person = this.persons.findById(id);
        if (person.isPresent()) {
            return new ResponseEntity<User>(
                    person.orElse(new User()),
                    HttpStatus.OK
            );
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не найден!");
    }

    @PostMapping("/")
    @Validated(Operation.OnCreate.class)
    public ResponseEntity<User> create(@Valid @RequestBody User user) {
       if (user.getUsername() == null || user.getPassword() == null) {
            throw new NullPointerException("Login and password mustn't be empty");
        }
        if (user.getPassword().length() < 3
                || user.getPassword().isEmpty()
                || user.getPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid password. Password length must be more than 3 characters.");
        }
        user.setPassword(encoder.encode(user.getPassword()));
        var result = this.persons.add(user);
        return new ResponseEntity<User>(
                result.orElse(new User()),
                result.isPresent() ? HttpStatus.CREATED : HttpStatus.CONFLICT
        );
    }

    @PutMapping("/")
    @Validated(Operation.OnUpdate.class)
     public ResponseEntity<Boolean> update(@Valid @RequestBody UserDto person) {
           if ((this.persons.updatePatch(person))) {
            return ResponseEntity.ok().build();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не обновлен!");
    }

    @DeleteMapping("/{id}")
    @Validated(Operation.OnDelete.class)
    public ResponseEntity<Boolean> delete(@Valid @PathVariable int id) {
        User user = new User();
        user.setId(id);
        if ((this.persons.delete(user))) {
            return ResponseEntity.ok().build();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не удален!");
    }

    @ExceptionHandler(value = { IllegalArgumentException.class })
    public void exceptionHandler(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new HashMap<>() { {
            put("message", e.getMessage());
            put("type", e.getClass());
        }}));
        LOGGER.error(e.getLocalizedMessage());
    }

    @GetMapping("/getCurrentUser")
    public ResponseEntity<User> getCurrentUser(@CurrentSecurityContext(expression = "authentication?.name")
                                                   String username) {
        var person = personsData.findUserByUsername(username);
        if (person != null) {
            return new ResponseEntity<>(
                    person,
                    HttpStatus.OK
            );
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не найден!");
    }

    @GetMapping("/findByUserName")
    public ResponseEntity<List<User>> findUsersByUsernameContains(@RequestParam(value = "userName") String userName) {
        var personsList = this.persons.findUserByUsernameContains(userName);
        if (!personsList.isEmpty()) {
            return new ResponseEntity<>(
                    personsList,
                    HttpStatus.OK
            );
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не найден!");
    }

}
