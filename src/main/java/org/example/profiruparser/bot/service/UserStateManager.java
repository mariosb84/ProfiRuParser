package org.example.profiruparser.bot.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserStateManager {

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, String> tempUsernames = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> userKeyWords = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userIntervals = new ConcurrentHashMap<>();

    /* Константы состояний*/
    public static final String STATE_NONE = "NONE";
    public static final String STATE_WAITING_USERNAME = "WAITING_FOR_USERNAME";
    public static final String STATE_WAITING_PASSWORD = "WAITING_FOR_PASSWORD";
    public static final String STATE_REGISTER_USERNAME = "REGISTER_USERNAME";
    public static final String STATE_REGISTER_PASSWORD = "REGISTER_PASSWORD";
    public static final String STATE_WAITING_PAYMENT_ID = "WAITING_FOR_PAYMENT_ID";
    public static final String STATE_WAITING_KEYWORD_1 = "WAITING_FOR_KEYWORD_1";
    public static final String STATE_WAITING_KEYWORD_2 = "WAITING_FOR_KEYWORD_2";
    public static final String STATE_WAITING_KEYWORD_3 = "WAITING_FOR_KEYWORD_3";
    public static final String STATE_WAITING_KEYWORD_4 = "WAITING_FOR_KEYWORD_4";
    public static final String STATE_WAITING_KEYWORD_5 = "WAITING_FOR_KEYWORD_5";
    public static final String STATE_AUTHORIZED_MAIN = "AUTHORIZED_MAIN_MENU";
    public static final String STATE_AUTHORIZED_KEYWORDS = "AUTHORIZED_KEYWORDS_MENU";
    public static final String STATE_AUTO_SEARCH = "AUTO_SEARCH_SETTINGS";
    public static final String STATE_WAITING_INTERVAL = "WAITING_FOR_INTERVAL";
    public static final String STATE_SUBSCRIPTION_MENU = "SUBSCRIPTION_MENU";
    public static final String STATE_SEARCH_IN_PROGRESS = "SEARCH_IN_PROGRESS";
    public static final String STATE_WAITING_SEARCH_CONFIRMATION = "WAITING_SEARCH_CONFIRMATION";
    public static final String STATE_WAITING_SEARCH_QUERY = "WAITING_SEARCH_QUERY";
    /* Добавляем в константы состояний:*/
    public static final String STATE_CHANGE_CREDENTIALS_USERNAME = "CHANGE_CREDENTIALS_USERNAME";
    public static final String STATE_CHANGE_CREDENTIALS_PASSWORD = "CHANGE_CREDENTIALS_PASSWORD";

    public String getUserState(Long chatId) {
        return userStates.getOrDefault(chatId, STATE_NONE);
    }

    public void setUserState(Long chatId, String state) {
        userStates.put(chatId, state);
    }

    public void removeUserState(Long chatId) {
        userStates.remove(chatId);
    }

    public String getTempUsername(Long chatId) {
        return tempUsernames.get(chatId);
    }

    public void setTempUsername(Long chatId, String username) {
        tempUsernames.put(chatId, username);
    }

    public void removeTempUsername(Long chatId) {
        tempUsernames.remove(chatId);
    }

    public List<String> getUserKeywords(Long chatId) {
        return userKeyWords.get(chatId);
    }

    public void setUserKeywords(Long chatId, List<String> keywords) {
        userKeyWords.put(chatId, keywords);
    }

    public void removeUserKeywords(Long chatId) {
        userKeyWords.remove(chatId);
    }

    public Integer getUserInterval(Long chatId) {
        return userIntervals.get(chatId);
    }

    public void setUserInterval(Long chatId, Integer interval) {
        userIntervals.put(chatId, interval);
    }

    public void removeUserInterval(Long chatId) {
        userIntervals.remove(chatId);
    }

    public void clearUserData(Long chatId) {
        userStates.remove(chatId);
        tempUsernames.remove(chatId);
        userKeyWords.remove(chatId);
        userIntervals.remove(chatId);
    }

    public void resetKeywordState(Long chatId) {
        String state = getUserState(chatId);
        if (state != null && state.startsWith("WAITING_FOR_KEYWORD_")) {
            setUserState(chatId, STATE_AUTHORIZED_KEYWORDS);
        }
    }

    private final Map<Long, String> tempSearchQueries = new ConcurrentHashMap<>();

    public void setTempSearchQuery(Long chatId, String query) {
        tempSearchQueries.put(chatId, query);
    }

    public String getTempSearchQuery(Long chatId) {
        return tempSearchQueries.get(chatId);
    }

    public void removeTempSearchQuery(Long chatId) {
        tempSearchQueries.remove(chatId);
    }

}



