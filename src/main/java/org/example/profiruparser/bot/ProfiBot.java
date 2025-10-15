package org.example.profiruparser.bot;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.bot.handlers.CallbackHandler;
import org.example.profiruparser.bot.handlers.MessageHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfiBot extends TelegramLongPollingBot {

    @Value("${tg.username}")
    private String username;

    @Value("${tg.token}")
    private String token;

    private final MessageHandler messageHandler;
    private final CallbackHandler callbackHandler;

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                callbackHandler.handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                messageHandler.handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", e.getMessage());
            messageHandler.handleError(update, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        messageHandler.shutdown();
    }

}
