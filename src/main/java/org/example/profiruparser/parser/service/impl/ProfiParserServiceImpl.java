package org.example.profiruparser.parser.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.parser.service.ProfiParserService;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j /** ДОБАВЛЯЕМ ЛОГГЕР */
@Service
public class ProfiParserServiceImpl implements ProfiParserService {

    private final WebDriverManager webDriverManager;
    private final LoginService loginService;
    private final SearchService searchService;
    private final OrderExtractionService orderExtractionService;

    /* ✅ ДОБАВИТЬ ЭТИ 2 СТРОЧКИ:*/
    private int searchCounter = 0;
    private static final int MAX_SEARCHES = 3;

    @Autowired
    public ProfiParserServiceImpl(
            @Qualifier("seleniumWebDriverManager") WebDriverManager webDriverManager,
            @Qualifier("parserLoginService") LoginService loginService,
            @Qualifier("parserSearchService") SearchService searchService,
            @Qualifier("orderExtractionService") OrderExtractionService orderExtractionService) {

        this.webDriverManager = webDriverManager;
        this.loginService = loginService;
        this.searchService = searchService;
        this.orderExtractionService = orderExtractionService;
    }

    @Override
    public List<ProfiOrder> parseOrders(String keyword) throws Exception {
        if (!loginService.isLoggedIn()) {
            throw new IllegalStateException("Требуется авторизация");
        }

        /* ✅ ДОБАВИТЬ ЭТОТ БЛОК ПЕРЕД ПОИСКОМ:*/
        if (searchCounter >= MAX_SEARCHES) {
            System.out.println("Auto-restarting browser after " + MAX_SEARCHES + " searches");
            this.close();
            /* Нужно перелогиниться после перезапуска*/
            /* this.ensureLoggedIn(login, password);*/  /*если есть доступ к логину/паролю*/
            searchCounter = 0;
        }

        searchCounter++;

        return searchService.searchOrders(keyword, orderExtractionService);
    }

    @Override
    public void ensureLoggedIn(String login, String password) throws LoginException {
        loginService.performLogin(login, password);
    }

    @Override
    public void close() {
        /** УЛУЧШЕННЫЙ МЕТОД ЗАКРЫТИЯ С ОБРАБОТКОЙ ОШИБОК */
        try {
            if (webDriverManager != null) {
                webDriverManager.quitDriver();
                log.info("Browser successfully closed");
            }
        } catch (Exception e) {
            log.warn("Browser already closed or not available: {}", e.getMessage());
            /** ИГНОРИРУЕМ ОШИБКИ - ВАЖНО ЧТО РЕСУРСЫ ОСВОБОЖДЕНЫ */
        }
    }

    /* ДОБАВИТЬ ЭТОТ МЕТОД*/
    @Override
    public WebDriver getDriver() {
        return webDriverManager.getDriver();
    }

}

