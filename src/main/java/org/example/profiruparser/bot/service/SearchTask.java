package org.example.profiruparser.bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SearchTask {

    private Long chatId;
    private String query;
    private SearchType type;
    private LocalDateTime addedTime;
    private int positionInQueue;

    public enum SearchType {
        MANUAL, KEYWORDS
    }

}