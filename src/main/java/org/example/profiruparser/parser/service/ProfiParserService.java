package org.example.profiruparser.parser.service;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.WebDriver;

import java.util.List;

public interface ProfiParserService {
    List<ProfiOrder> parseOrders(String keyword) throws Exception;
    void ensureLoggedIn(String login, String password) throws LoginException;
    void close();
    WebDriver getDriver(); /* ДОБАВИТЬ ЭТОТ МЕТОД*/

}