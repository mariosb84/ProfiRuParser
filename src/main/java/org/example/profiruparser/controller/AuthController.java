package org.example.profiruparser.controller;

import jakarta.validation.Valid;
import org.example.profiruparser.domain.dto.SignInRequest;
import org.example.profiruparser.domain.dto.SignUpRequest;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationService authenticationService;

    @PostMapping("/sign-up")
    public Optional<User> signUp(@RequestBody @Valid SignUpRequest request) {
        return authenticationService.signUp(request);
    }

    @PostMapping("/sign-in")
    public Optional<User> signIn(@RequestBody @Valid SignInRequest request) {
        return authenticationService.signIn(request);
    }

}
