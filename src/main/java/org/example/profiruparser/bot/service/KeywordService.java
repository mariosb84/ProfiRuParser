package org.example.profiruparser.bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final UserStateManager stateManager;

    public void handleEditKeywordCommand(Long chatId, String text) {
        try {
            int keyNum = Integer.parseInt(text.replace("✏️ Ключ ", ""));
            if (keyNum >= 1 && keyNum <= 5) {
                stateManager.setUserState(chatId, "WAITING_FOR_KEYWORD_" + keyNum);
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка обработки команды редактирования ключа");
        }
    }

    public void handleKeywordInput(Long chatId, String text) {
        try {
            String userState = stateManager.getUserState(chatId);
            int keyNum = Integer.parseInt(userState.replace("WAITING_FOR_KEYWORD_", ""));

            if (isMenuCommand(text)) {
                throw new IllegalArgumentException("Нельзя использовать команды меню как ключевое слово");
            }

            saveKeyword(chatId, keyNum - 1, text);

            // ВОССТАНАВЛИВАЕМ состояние после сохранения ключа
            stateManager.setUserState(chatId, UserStateManager.STATE_AUTHORIZED_KEYWORDS);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка сохранения ключа: " + e.getMessage());
        }
    }

    private boolean isMenuCommand(String text) {
        return text.equals("🧹 Очистить все") ||
                text.equals("🚀 Поиск по ключам") ||
                text.equals("🔙 Назад") ||
                text.startsWith("✏️ Ключ ");
    }

    public void clearAllKeywords(Long chatId) {
        stateManager.setUserKeywords(chatId, Arrays.asList("", "", "", "", ""));
    }

    private void saveKeyword(Long chatId, int index, String keyword) {
        List<String> keywords = stateManager.getUserKeywords(chatId);
        if (keywords == null) {
            keywords = new ArrayList<>(Arrays.asList("", "", "", "", ""));
        }

        if (index >= 0 && index < keywords.size()) {
            keywords.set(index, keyword);
            stateManager.setUserKeywords(chatId, keywords);
        }
    }

    public List<String> getKeywordsForDisplay(Long chatId) {
        List<String> keywords = stateManager.getUserKeywords(chatId);
        return keywords != null ? keywords : Arrays.asList("", "", "", "", "");
    }

    public List<String> getActiveKeywords(Long chatId) {
        List<String> keywords = getKeywordsForDisplay(chatId);
        return keywords.stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .toList();
    }

}