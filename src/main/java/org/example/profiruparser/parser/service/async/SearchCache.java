package org.example.profiruparser.parser.service.async;

import org.example.profiruparser.domain.dto.ProfiOrder;
import java.util.List;
import java.util.Optional;

/* 💾 Интерфейс для кэширования результатов поиска */
public interface SearchCache {

    /* Сохранить результаты поиска в кэш */
    void put(String keyword, List<ProfiOrder> results);

    /* Получить результаты из кэша */
    Optional<List<ProfiOrder>> get(String keyword);

    /* Удалить результаты из кэша */
    void invalidate(String keyword);

    /* Очистить весь кэш */
    void clear();

    /* Получить количество записей в кэше */
    int size();
}