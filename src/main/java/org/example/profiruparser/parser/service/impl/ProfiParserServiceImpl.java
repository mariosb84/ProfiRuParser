package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.parser.service.ProfiParserService;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfiParserServiceImpl implements ProfiParserService {

    private final WebDriverManager webDriverManager;
    private final LoginService loginService;
    private final SearchService searchService;
    private final OrderExtractionService orderExtractionService;

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
        return searchService.searchOrders(keyword, orderExtractionService);
    }

    @Override
    public void ensureLoggedIn(String login, String password) throws LoginException {
        loginService.performLogin(login, password);
    }

    @Override
    public void close() {
        webDriverManager.quitDriver();
    }

    /* ДОБАВИТЬ ЭТОТ МЕТОД*/
    @Override
    public WebDriver getDriver() {
        return webDriverManager.getDriver();
    }

}
