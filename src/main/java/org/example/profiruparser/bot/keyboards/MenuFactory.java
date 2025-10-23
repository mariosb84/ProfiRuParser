package org.example.profiruparser.bot.keyboards;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

public interface MenuFactory {
    SendMessage createWelcomeMenu(Long chatId);
    SendMessage createMainMenu(Long chatId);
    SendMessage createMainMenu(Long chatId, boolean afterSearch); /* ← ДОБАВЛЯЕМ*/
    SendMessage createSubscriptionMenu(Long chatId);
    SendMessage createKeywordsMenu(Long chatId, List<String> keywords); /* ← ИЗМЕНИЛСЯ*/
    SendMessage createInfoMenu(Long chatId);        /* ← ДОБАВЛЯЕМ*/
    SendMessage createContactsMenu(Long chatId);    /* ← ДОБАВЛЯЕМ*/
}