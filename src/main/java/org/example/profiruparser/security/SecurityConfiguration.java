package org.example.profiruparser.security;

import org.example.profiruparser.service.UserServiceData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;


import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final UserServiceData userService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    /*ТЕСТИРУЕМ ДЛЯ ПЕРЕДАЧИ КОНТЕКСТА БЕЗОПАСНОСТИ В АСИНХРОННЫЕ МЕТОДЫ :*/
    /*ВОЗМОЖНЫ УТЕЧКИ ПАМЯТИ!!!*/
    /*ASYNC TESTING*/
   /* @PostConstruct
    public void setSecurityContextHolderStrategy() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }*/
    /*ASYNC TESTING*/

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                /* Своего рода отключение CORS (разрешение запросов со всех доменов)*/
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfiguration = new CorsConfiguration();
                    corsConfiguration.setAllowedOriginPatterns(List.of("*"));
                    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    corsConfiguration.setAllowedHeaders(List.of("*"));
                    corsConfiguration.setAllowCredentials(true);
                    return corsConfiguration;
                }))
                /* Настройка доступа к конечным точкам*/
                .authorizeHttpRequests(request -> {
                    try {
                        request
                                /* Можно указать конкретный путь, * - 1 уровень вложенности, ** - любое количество уровней вложенности*/
                                .requestMatchers(

                                        /* ВНУТРЕННИЕ СЕРВИСЫ : */
                                        "/auth/**")

                                .permitAll()
                                .anyRequest().authenticated();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })

                /* Не позволяет создавать сессию, данные пользователя не сохраняются на сервере.*/
                /*.sessionManagement(manager -> manager.sessionCreationPolicy(STATELESS))*/

                /*  Если вы хотите создавать сессию для каждого запроса, независимо от того,
                 есть ли данные в сессии или нет, то можно использовать sessionCreationPolicy(ALWAYS)..*/
                /*.sessionManagement(manager -> manager.sessionCreationPolicy(ALWAYS))*/

                /*  Если вы хотите создавать сессию только в том случае, если в ней есть данные,
                 то можно использовать sessionCreationPolicy(IF_REQUIRED).)..*/

                .sessionManagement(manager -> manager.sessionCreationPolicy(IF_REQUIRED))
                .authenticationProvider(authenticationProvider());
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService.userDetailsService());
        authProvider.setPasswordEncoder(bCryptPasswordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

}
