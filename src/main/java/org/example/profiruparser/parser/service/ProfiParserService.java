package org.example.profiruparser.parser.service;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;

import java.util.List;

public interface ProfiParserService {
    List<ProfiOrder> parseOrders(String keyword) throws Exception;
    void ensureLoggedIn(String login, String password) throws LoginException;
    void close();

}