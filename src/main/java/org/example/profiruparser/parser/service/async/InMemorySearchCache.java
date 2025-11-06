package org.example.profiruparser.parser.service.async;

import lombok.Getter;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* 💾 In-memory реализация кэша с TTL (время жизни) */
@Service
public class InMemorySearchCache implements SearchCache {

    /* Хранилище кэша: ключевое слово -> результаты */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /* TTL (Time To Live) в миллисекундах (5 минут) */
    private static final long TTL_MS = 5 * 60 * 1000;

    /* Планировщик для очистки устаревших записей */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor();

    public InMemorySearchCache() {
        /* Запускаем периодическую очистку устаревших записей */
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.MINUTES);
        System.out.println("💾 InMemorySearchCache initialized with TTL: " + (TTL_MS/1000/60) + " minutes");
    }

    @Override
    public void put(String keyword, List<ProfiOrder> results) {
        String normalizedKey = normalizeKey(keyword);
        CacheEntry entry = new CacheEntry(results, System.currentTimeMillis());
        cache.put(normalizedKey, entry);
        System.out.println("💾 Cached results for: '" + keyword + "' -> " + results.size() + " orders");
    }

    @Override
    public Optional<List<ProfiOrder>> get(String keyword) {
        String normalizedKey = normalizeKey(keyword);
        CacheEntry entry = cache.get(normalizedKey);

        if (entry != null && !entry.isExpired()) {
            System.out.println("💾 Cache HIT for: '" + keyword + "'");
            return Optional.of(entry.getResults());
        }

        /* Если запись устарела - удаляем ее */
        if (entry != null && entry.isExpired()) {
            cache.remove(normalizedKey);
            System.out.println("🗑️ Removed expired cache entry for: '" + keyword + "'");
        } else {
            System.out.println("💾 Cache MISS for: '" + keyword + "'");
        }

        return Optional.empty();
    }

    @Override
    public void invalidate(String keyword) {
        String normalizedKey = normalizeKey(keyword);
        cache.remove(normalizedKey);
        System.out.println("🗑️ Invalidated cache for: '" + keyword + "'");
    }

    @Override
    public void clear() {
        int size = cache.size();
        cache.clear();
        System.out.println("🧹 Cache cleared. Removed " + size + " entries");
    }

    @Override
    public int size() {
        return cache.size();
    }

    /* 🔧 Нормализация ключа (приведение к нижнему регистру, удаление пробелов) */
    private String normalizeKey(String keyword) {
        return keyword.toLowerCase().trim();
    }

    /* 🧹 Очистка устаревших записей */
    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        int initialSize = cache.size();

        cache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                System.out.println("🧹 Auto-removing expired cache: '" + entry.getKey() + "'");
            }
            return expired;
        });

        int removed = initialSize - cache.size();
        if (removed > 0) {
            System.out.println("🧹 Cache cleanup: removed " + removed + " expired entries");
        }
    }

    /* 🛑 Остановка планировщика при завершении */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /* 📦 Внутренний класс для хранения записи кэша */
    private static class CacheEntry {
        @Getter
        private final List<ProfiOrder> results;
        private final long timestamp;

        public CacheEntry(List<ProfiOrder> results, long timestamp) {
            this.results = results;
            this.timestamp = timestamp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL_MS;
        }
    }

}