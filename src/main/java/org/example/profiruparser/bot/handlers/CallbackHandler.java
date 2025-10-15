package org.example.profiruparser.bot.handlers;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

public interface CallbackHandler {
    void handleCallback(CallbackQuery callbackQuery);
}

