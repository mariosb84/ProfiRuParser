package org.example.profiruparser.parser.service.async;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/* 🎛️ Оркестратор для управления сложными сценариями поиска */
@Service
public class SearchOrchestrator {

    private final AsyncProfiParserService asyncParserService;
    private final SearchCache searchCache;

    @Autowired
    public SearchOrchestrator(AsyncProfiParserService asyncParserService,
                              SearchCache searchCache) {
        this.asyncParserService = asyncParserService;
        this.searchCache = searchCache;
    }

    /* 🔍 Умный поиск с кэшированием */
    public CompletableFuture<List<ProfiOrder>> smartSearch(String keyword, String sessionId) {
        System.out.println("🎯 Smart search for: '" + keyword + "'");

        /* 1. Проверяем кэш */
        var cachedResult = searchCache.get(keyword);
        if (cachedResult.isPresent()) {
            System.out.println("⚡ Returning cached results for: '" + keyword + "'");
            return CompletableFuture.completedFuture(cachedResult.get());
        }

        /* 2. Если нет в кэше - выполняем поиск */
        return asyncParserService.parseOrdersAsync(keyword, sessionId)
                .thenApply(results -> {
                    /* 3. Сохраняем результаты в кэш */
                    searchCache.put(keyword, results);
                    System.out.println("💾 Saved to cache: '" + keyword + "' -> " + results.size() + " orders");
                    return results;
                });
    }

    /* 🔄 Пакетный поиск по нескольким ключевым словам */
    public CompletableFuture<List<List<ProfiOrder>>> batchSearch(List<String> keywords, String sessionId) {
        System.out.println("🔄 Batch search for " + keywords.size() + " keywords");

        /* Создаем асинхронные задачи для каждого ключевого слова */
        List<CompletableFuture<List<ProfiOrder>>> futures = keywords.stream()
                .map(keyword -> smartSearch(keyword, sessionId))
                .toList();

        /* Ждем завершения всех задач */
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /* 🗑️ Очистка кэша для конкретного ключевого слова */
    public void invalidateCache(String keyword) {
        searchCache.invalidate(keyword);
        System.out.println("🗑️ Cache invalidated for: '" + keyword + "'");
    }

    /* 📊 Статистика кэша */
    public void printCacheStats() {
        System.out.println("📊 Cache statistics: " + searchCache.size() + " entries");
    }

}