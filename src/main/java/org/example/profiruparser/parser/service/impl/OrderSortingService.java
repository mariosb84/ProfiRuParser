package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.domain.dto.ProfiOrder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class OrderSortingService {

    public List<ProfiOrder> sortOrdersByDate(List<ProfiOrder> orders) {
        orders.sort((o1, o2) -> {
            try {
                long weight1 = getDateWeight(o1.getCreationTime());
                long weight2 = getDateWeight(o2.getCreationTime());

                /* Сортируем по УБЫВАНИЮ веса (новые первыми)*/
                return Long.compare(weight2, weight1);
            } catch (Exception e) {
                return 0;
            }
        });

        logSortedOrders(orders);
        return orders;
    }

    private long getDateWeight(String time) {
        if (time == null || time.equals("Неизвестно")) {
            return 0;
        }

        String lowerTime = time.toLowerCase().trim();

        /* 1. Сегодняшние заказы (самые высокие веса)*/
        if (lowerTime.contains("только что")) {
            return 1_000_000;
        } else if (lowerTime.contains("минут")) {
            int mins = extractNumber(lowerTime);
            return 1_000_000 - mins;
        } else if (lowerTime.contains("час")) {
            int hours = extractNumber(lowerTime);
            return 1_000_000 - (hours * 60L);

            /* 2. Вчерашние заказы*/
        } else if (lowerTime.contains("вчера")) {
            int timeMinutes = parseYesterdayTime(lowerTime);
            return 900_000 + timeMinutes;

            /* 3. Старые заказы (по датам)*/
        } else {
            return parseAbsoluteDateWeight(lowerTime);
        }
    }

    private long parseAbsoluteDateWeight(String date) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate orderDate;

            if (date.contains("октября") || date.contains("ноября") || date.contains("декабря") ||
                    date.contains("января") || date.contains("февраля") || date.contains("марта") ||
                    date.contains("апреля") || date.contains("мая") || date.contains("июня") ||
                    date.contains("июля") || date.contains("августа") || date.contains("сентября")) {

                int day = extractNumber(date);
                int month = getMonthFromString(date);
                orderDate = LocalDate.of(today.getYear(), month, day);

            } else if (date.contains(".")) {
                String[] parts = date.split("\\.");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = parts.length > 2 ? Integer.parseInt(parts[2]) : today.getYear();
                orderDate = LocalDate.of(year, month, day);
            } else {
                int day = extractNumber(date);
                orderDate = LocalDate.of(today.getYear(), today.getMonth(), day);
            }

            /* Считаем разницу в днях от сегодняшней даты*/
            long daysBetween = ChronoUnit.DAYS.between(orderDate, today);

            /* Чем меньше дней прошло, тем больше вес*/
            return Math.max(0, 100000 - daysBetween * 1000);

        } catch (Exception e) {
            System.err.println("Error parsing date: " + date);
            return 0;
        }
    }

    private int getMonthFromString(String date) {
        if (date.contains("январ")) return 1;
        if (date.contains("феврал")) return 2;
        if (date.contains("март")) return 3;
        if (date.contains("апрел")) return 4;
        if (date.contains("май") || date.contains("мая")) return 5;
        if (date.contains("июн")) return 6;
        if (date.contains("июл")) return 7;
        if (date.contains("август")) return 8;
        if (date.contains("сентябр")) return 9;
        if (date.contains("октябр")) return 10;
        if (date.contains("ноябр")) return 11;
        if (date.contains("декабр")) return 12;
        return LocalDate.now().getMonthValue();
    }

    private int parseYesterdayTime(String time) {
        try {
            System.out.println("DEBUG: Parsing yesterday time: " + time);

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2})[.:]?(\\d{2})?");
            java.util.regex.Matcher matcher = pattern.matcher(time);

            if (matcher.find()) {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = 0;
                if (matcher.group(2) != null) {
                    minutes = Integer.parseInt(matcher.group(2));
                }
                int totalMinutes = hours * 60 + minutes;
                System.out.println("DEBUG: Regex parsed time: " + hours + ":" + minutes + " = " + totalMinutes + " minutes");
                return totalMinutes;
            }

        } catch (Exception e) {
            System.err.println("Error parsing yesterday time: " + time + " - " + e.getMessage());
        }

        return 0;
    }

    private int extractNumber(String text) {
        try {
            String numbers = text.replaceAll("[^0-9]", "").trim();
            return numbers.isEmpty() ? 0 : Integer.parseInt(numbers);
        } catch (Exception e) {
            return 0;
        }
    }

    private void logSortedOrders(List<ProfiOrder> orders) {
        System.out.println("=== FULL SORTED ORDERS CHECK ===");
        for (int i = 0; i < orders.size(); i++) {
            ProfiOrder order = orders.get(i);
            String time = order.getCreationTime();
            long weight = getDateWeight(time);
            System.out.printf("%2d: %s (%d weight) - %s%n",
                    i, time, weight,
                    order.getTitle().length() > 30 ? order.getTitle().substring(0, 30) + "..." : order.getTitle());
        }
    }

}
