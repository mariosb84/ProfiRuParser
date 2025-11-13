package org.example.profiruparser.bot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.model.User;
import org.example.profiruparser.service.UserServiceData;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/*import java.util.Queue;*/
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueueService {

    /*private final Queue<SearchTask> queue = new ConcurrentLinkedQueue<>();*/
    /* ЗАМЕНИТЬ НА BlockingQueue*/
    private final BlockingQueue<SearchTask> queue = new LinkedBlockingQueue<>();
    private final Map<Long, SearchTask> userTasks = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSearchTime = new ConcurrentHashMap<>();
    private final Semaphore browserSemaphore = new Semaphore(3);

    private static final long MIN_SEARCH_INTERVAL_MS = 2 * 60 * 1000; /* 2 минуты*/

    private final SearchService searchService;
    private final TelegramService telegramService;
    private final UserStateManager stateManager;
    private final UserServiceData userServiceData;

   /* @PostConstruct
    public void startWorkers() {
        for (int i = 0; i < 3; i++) {
            new Thread(this::processQueue, "SearchWorker-" + i).start();
        }
    }*/

    @PostConstruct
    public void startWorkers() {
        for (int i = 0; i < 3; i++) {
            Thread worker = new Thread(this::processQueue, "SearchWorker-" + i);
            worker.setDaemon(true); /* Демон-потоки*/
            worker.start();
        }
        log.info("✅ Started 3 SearchWorker threads with BlockingQueue");
    }

    /*public void addToQueue(Long chatId, String query, SearchTask.SearchType type) {
        *//* Проверяем лимит 1 поиск в 2 минуты*//*
        Long lastSearch = lastSearchTime.get(chatId);
        if (lastSearch != null && System.currentTimeMillis() - lastSearch < MIN_SEARCH_INTERVAL_MS) {
            long waitTime = MIN_SEARCH_INTERVAL_MS - (System.currentTimeMillis() - lastSearch);
            telegramService.sendMessage(chatId,
                    "⏳ Следующий поиск будет доступен через " + (waitTime / 1000 / 60) + " минут");
            return;
        }

        *//* Создаем задачу*//*
        SearchTask task = new SearchTask(chatId, query, type, LocalDateTime.now(), queue.size() + 1);
        queue.offer(task);
        userTasks.put(chatId, task);

        *//* Отправляем статус*//*
        telegramService.sendMessage(chatId,
                "⏳ Добавлен в очередь. Позиция: " + task.getPositionInQueue() +
                        "\nОжидание: ~" + (task.getPositionInQueue() * 40 / 60) + " минут");

        updateQueuePositions();
    }*/

    public void addToQueue(Long chatId, String query, SearchTask.SearchType type) {
        /* Проверяем лимит 1 поиск в 2 минуты*/
        Long lastSearch = lastSearchTime.get(chatId);
        if (lastSearch != null && System.currentTimeMillis() - lastSearch < MIN_SEARCH_INTERVAL_MS) {
            long waitTime = MIN_SEARCH_INTERVAL_MS - (System.currentTimeMillis() - lastSearch);
            telegramService.sendMessage(chatId,
                    "⏳ Следующий поиск будет доступен через " + (waitTime / 1000 / 60) + " минут");
            return;
        }

        /* Создаем задачу - размер очереди может меняться, поэтому рассчитываем позицию*/
        int position = queue.size() + 1;
        SearchTask task = new SearchTask(chatId, query, type, LocalDateTime.now(), position);

        /* Добавляем в очередь*/
        queue.offer(task);
        userTasks.put(chatId, task);

        /* Отправляем статус*/
        telegramService.sendMessage(chatId,
                "⏳ Добавлен в очередь. Позиция: " + task.getPositionInQueue() +
                        "\nОжидание: ~" + (task.getPositionInQueue() * 40 / 60) + " минут");

        updateQueuePositions();
    }

  /*  private void processQueue() {
        while (true) {
            try {
                browserSemaphore.acquire(); *//* Ждем свободный браузер*//*

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
    }*/

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.debug("🔄 SearchWorker waiting for browser...");
                browserSemaphore.acquire(); /* Ждем свободный браузер*/

                log.debug("✅ Browser acquired, waiting for task...");
                /* БЛОКИРУЮЩИЙ вызов - поток ждет пока появится задача*/
                SearchTask task = queue.take();

                log.info("🎯 Processing task for chatId: {}, type: {}",
                        task.getChatId(), task.getType());
                processTask(task);

            } catch (InterruptedException e) {
                log.info("SearchWorker interrupted");
                Thread.currentThread().interrupt();
                break;
            } finally {
                browserSemaphore.release();
                log.debug("🔓 Browser released");
            }
        }
        log.info("SearchWorker stopped");
    }

    private void processTask(SearchTask task) {
        log.info("🎯 START PROCESSING TASK - ChatId: {}, Type: {}", task.getChatId(), task.getType());

        try {
            /* Обновляем время последнего поиска*/
            lastSearchTime.put(task.getChatId(), System.currentTimeMillis());

            /* Уведомляем о начале поиска*/
            telegramService.sendMessage(task.getChatId(), "🔍 Начинаю поиск...");
            log.info("📢 SENT START MESSAGE TO USER");

            User user = userServiceData.findByTelegramChatId(task.getChatId());
            log.info("👤 USER FOUND: {}", user != null ? user.getUsername() : "NULL");

            /* Выполняем поиск*/
            if (task.getType() == SearchTask.SearchType.MANUAL) {
                log.info("📝 CALLING handleManualSearch - Query: {}", task.getQuery());
                /*searchService.executeManualSearch(task.getChatId(), task.getQuery());*/
                searchService.handleManualSearch(task.getChatId(), task.getQuery()); /* ← ИЗМЕНИЛ*/
            } else {
                log.info("🔑 CALLING searchByKeywords");
                /*searchService.executeKeywordSearch(task.getChatId());*/
                searchService.searchByKeywords(task.getChatId()); /* ← ИЗМЕНИЛ*/
            }

            log.info("✅ TASK COMPLETED SUCCESSFULLY");

        } catch (Exception e) {
            log.error("❌ TASK FAILED - Error: {}", e.getMessage(), e);
            telegramService.sendMessage(task.getChatId(), "❌ Ошибка при поиске: " + e.getMessage());
        } finally {
            userTasks.remove(task.getChatId());
            updateQueuePositions();
            log.info("🧹 TASK CLEANED UP");
        }
    }

    /*private void updateQueuePositions() {
        int position = 1;
        for (SearchTask task : queue) {
            task.setPositionInQueue(position++);
        }
    }*/

    private void updateQueuePositions() {
        int position = 1;
        /* Преобразуем в список чтобы избежать ConcurrentModificationException*/
        List<SearchTask> tasks = new ArrayList<>(queue);
        for (SearchTask task : tasks) {
            task.setPositionInQueue(position++);
        }
    }

    public int getQueuePosition(Long chatId) {
        SearchTask task = userTasks.get(chatId);
        return task != null ? task.getPositionInQueue() : 0;
    }

}

