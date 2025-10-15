package org.example.profiruparser.bot.handlers;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface MessageHandler {
    void handleTextMessage(Message message);
    void handleError(Update update, Exception exception);
    void shutdown();
}

