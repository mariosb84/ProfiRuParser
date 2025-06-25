package org.example.profiruparser.config;

import org.example.profiruparser.service.ProfiBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi(ProfiBot bot) {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            logger.info("Telegram bot registered successfully");
            return api;
        } catch (TelegramApiException e) {
            logger.error("Failed to register Telegram bot", e);
            throw new RuntimeException(e); /* или обработать иначе*/
        }
    }

}
