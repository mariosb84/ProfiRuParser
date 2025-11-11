package org.example.profiruparser.bot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueueService {

    private final Queue<SearchTask> queue = new ConcurrentLinkedQueue<>();
    private final Map<Long, SearchTask> userTasks = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSearchTime = new ConcurrentHashMap<>();
    private final Semaphore browserSemaphore = new Semaphore(3);

    private static final long MIN_SEARCH_INTERVAL_MS = 2 * 60 * 1000; /* 2 минуты*/

    private final SearchService searchService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;

    @PostConstruct
    public void startWorkers() {
        for (int i = 0; i < 3; i++) {
            new Thread(this::processQueue, "SearchWorker-" + i).start();
        }
    }

    public void addToQueue(Long chatId, String query, SearchTask.SearchType type) {
        /* Проверяем лимит 1 поиск в 2 минуты*/
        Long lastSearch = lastSearchTime.get(chatId);
        if (lastSearch != null && System.currentTimeMillis() - lastSearch < MIN_SEARCH_INTERVAL_MS) {
            long waitTime = MIN_SEARCH_INTERVAL_MS - (System.currentTimeMillis() - lastSearch);
            telegramService.sendMessage(chatId,
                    "⏳ Следующий поиск будет доступен через " + (waitTime / 1000 / 60) + " минут");
            return;
        }

        /* Создаем задачу*/
        SearchTask task = new SearchTask(chatId, query, type, LocalDateTime.now(), queue.size() + 1);
        queue.offer(task);
        userTasks.put(chatId, task);

        /* Отправляем статус*/
        telegramService.sendMessage(chatId,
                "⏳ Добавлен в очередь. Позиция: " + task.getPositionInQueue() +
                        "\nОжидание: ~" + (task.getPositionInQueue() * 40 / 60) + " минут");

        updateQueuePositions();
    }

    private void processQueue() {
        while (true) {
            try {
                browserSemaphore.acquire(); /* Ждем свободный браузер*/

                SearchTask task = queue.poll();
                if (task != null) {
                    processTask(task);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                browserSemaphore.release();
            }
        }
    }

    private void processTask(SearchTask task) {
        try {
            /* Обновляем время последнего поиска*/
            lastSearchTime.put(task.getChatId(), System.currentTimeMillis());

            /* Уведомляем о начале поиска*/
            telegramService.sendMessage(task.getChatId(), "🔍 Начинаю поиск...");

            /* Выполняем поиск*/
            if (task.getType() == SearchTask.SearchType.MANUAL) {
                searchService.executeManualSearch(task.getChatId(), task.getQuery());
            } else {
                searchService.executeKeywordSearch(task.getChatId());
            }

        } catch (Exception e) {
            log.error("Error processing search task for chatId: {}", task.getChatId(), e);
            telegramService.sendMessage(task.getChatId(), "❌ Ошибка при поиске");
        } finally {
            userTasks.remove(task.getChatId());
            updateQueuePositions();
        }
    }

    private void updateQueuePositions() {
        int position = 1;
        for (SearchTask task : queue) {
            task.setPositionInQueue(position++);
        }
    }

    public int getQueuePosition(Long chatId) {
        SearchTask task = userTasks.get(chatId);
        return task != null ? task.getPositionInQueue() : 0;
    }

}