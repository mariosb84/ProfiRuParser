package org.example.profiruparser.bot.keyboards;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

public interface MenuFactory {
    SendMessage createWelcomeMenu(Long chatId);
    SendMessage createMainMenu(Long chatId);
    SendMessage createSubscriptionMenu(Long chatId);
    SendMessage createKeywordsMenu(Long chatId, List<String> keywords); // ← ИЗМЕНИЛСЯ
    /*SendMessage createAutoSearchMenu(Long chatId);*/
}