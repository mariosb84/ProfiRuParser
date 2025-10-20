-- 1. Создание таблицы
CREATE TABLE seen_orders (
    user_id BIGINT NOT NULL,
    order_id VARCHAR(50) NOT NULL,
    seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, order_id),
    FOREIGN KEY (user_id) REFERENCES person(person_id)
);

-- 2. Создание индексов
-- Индекс для быстрого поиска заказов пользователя
CREATE INDEX idx_seen_orders_user_id ON seen_orders(user_id);
CREATE INDEX idx_seen_orders_seen_at ON seen_orders(seen_at);

-- Партиционирование по месяцам (опционально, для больших объемов)
-- CREATE TABLE seen_orders_2025_10 PARTITION OF seen_orders
--     FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

-- 3. Проверка (опционально)
COMMENT ON TABLE seen_orders IS 'Таблица для хранения просмотренных заказов пользователей';