package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service("orderExtractionService")
@Qualifier("orderExtractionService")
public class OrderExtractionService {

    public List<ProfiOrder> extractOrders(WebDriver driver, String keyword) {
        List<ProfiOrder> orders = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        List<WebElement> cards = driver.findElements(By.cssSelector("a[data-testid$='_order-snippet']"));
        System.out.println("Total cards to process: " + cards.size());

        for (int i = 0; i < cards.size(); i++) {
            try {
                WebElement card = cards.get(i);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", card);
                Thread.sleep(100);

                if (!card.isDisplayed()) continue;

                String title = extractTitle(card);
                if (title.isEmpty()) continue;

                String lowerTitle = title.toLowerCase();
                boolean matches = lowerTitle.contains(lowerKeyword) ||
                        matchesKeywordVariations(title, keyword);

                if (matches) {
                    ProfiOrder order = new ProfiOrder();
                    order.setId(card.getAttribute("id") != null ? card.getAttribute("id") : "id_" + i);
                    order.setTitle(title);
                    order.setPrice(extractPrice(card));
                    order.setDescription(extractDescription(card));
                    order.setCreationTime(extractCreationTime(card));

                    orders.add(order);
                    System.out.println("✅ ADDED: " + title + " | Time: " + order.getCreationTime());
                }
            } catch (Exception e) {
                System.err.println("Error processing card " + i + ": " + e.getMessage());
            }
        }

        return sortOrdersByDate(orders);
    }

    private List<ProfiOrder> sortOrdersByDate(List<ProfiOrder> orders) {
        orders.sort((o1, o2) -> {
            try {
                long minutes1 = parseTimeToMinutes(o1.getCreationTime());
                long minutes2 = parseTimeToMinutes(o2.getCreationTime());

                if (minutes1 > 24 * 60 && minutes2 > 24 * 60) {
                    // Оба старые - сортируем по убыванию (новые старые первыми)
                    return Long.compare(minutes1, minutes2);
                } else if (!isOldDate(minutes1) && !isOldDate(minutes2)) {
                    // Оба сегодняшние - сортируем по возрастанию (новые первыми)
                    return Long.compare(minutes1, minutes2);
                } else {
                    // Разные типы - сегодняшние всегда перед старыми
                    return isOldDate(minutes1) ? 1 : -1;
                }
            } catch (Exception e) {
                return 0;
            }
        });

        // ПОЛНАЯ ПРОВЕРКА ВСЕХ ОТСОРТИРОВАННЫХ ЗАКАЗОВ
        System.out.println("=== FULL SORTED ORDERS CHECK ===");
        for (int i = 0; i < orders.size(); i++) {
            ProfiOrder order = orders.get(i);
            String time = order.getCreationTime();
            long minutes = parseTimeToMinutes(time);
            String type = isOldDate(minutes) ? "OLD" : "TODAY";
            System.out.printf("%2d: %s (%d min, %s) - %s%n",
                    i, time, minutes, type,
                    order.getTitle().length() > 30 ? order.getTitle().substring(0, 30) + "..." : order.getTitle());
        }

        return orders;
    }

    private boolean isOldDate(long minutes) {
        return minutes > 24 * 60; // старше суток
    }

    private long parseTimeToMinutes(String time) {
        if (time == null || time.equals("Неизвестно")) {
            return Long.MAX_VALUE;
        }

        String lowerTime = time.toLowerCase();

        if (lowerTime.contains("только что")) {
            return 0;
        } else if (lowerTime.contains("минут")) {
            return Integer.parseInt(lowerTime.replaceAll("[^0-9]", ""));
        } else if (lowerTime.contains("час")) {
            int hours = Integer.parseInt(lowerTime.replaceAll("[^0-9]", ""));
            return hours * 60L;
        } else if (lowerTime.contains("вчера")) {
            return 24 * 60; // Просто 1 день, без времени
        } else {
            System.out.println("DEBUG: Parsing absolute date: '" + time + "'");
            long result = parseAbsoluteDateToMinutes(lowerTime);
            System.out.println("DEBUG: Date '" + time + "' -> " + result + " minutes");
            return result;
        }
    }

    private long parseAbsoluteDateToMinutes(String date) {
        try {
            int currentYear = LocalDate.now().getYear();

            String cleanDate = date.replaceAll("[^0-9.]", "").trim();

            if (cleanDate.contains(".")) {
                String[] parts = cleanDate.split("\\.");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = parts.length > 2 ? Integer.parseInt(parts[2]) : currentYear;

                LocalDate orderDate = LocalDate.of(year, month, day);
                LocalDate today = LocalDate.now();

                // МЕНЯЕМ ПОРЯДОК: today - orderDate (чтобы получить положительное значение)
                long daysBetween = ChronoUnit.DAYS.between(orderDate, today);

                return daysBetween * 24 * 60L;
            } else {
                int day = Integer.parseInt(cleanDate);
                int month = getMonthFromText(date);
                LocalDate orderDate = LocalDate.of(currentYear, month, day);
                LocalDate today = LocalDate.now();

                // МЕНЯЕМ ПОРЯДОК: today - orderDate
                long daysBetween = ChronoUnit.DAYS.between(orderDate, today);

                return daysBetween * 24 * 60L;
            }
        } catch (Exception e) {
            System.err.println("Error parsing date: " + date);
            return 100 * 24 * 60L;
        }
    }

    private int getMonthFromText(String date) {
        String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня",
                "июля", "августа", "сентября", "октября", "ноября", "декабря"};

        for (int i = 0; i < months.length; i++) {
            if (date.contains(months[i])) {
                return i + 1;
            }
        }
        return 10; // октябрь по умолчанию, если месяц не распознан
    }

    private int parseYesterdayTime(String time) {
        try {
            String timePart = time.replace("вчера в", "").trim();
            timePart = timePart.replaceAll("[^0-9:]", "");

            String[] parts = timePart.split(":");
            if (parts.length >= 2) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60 + minutes;
            } else if (parts.length == 1) {
                int hours = Integer.parseInt(parts[0]);
                return hours * 60;
            }
        } catch (Exception e) {
            System.err.println("Error parsing yesterday time: " + time);
        }
        return 0;
    }

    private boolean matchesKeywordVariations(String title, String keyword) {
        String lowerTitle = title.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        if (lowerKeyword.equals("юрист")) {
            return lowerTitle.contains("юрист") ||
                    lowerTitle.contains("юридич") ||
                    lowerTitle.contains("юрид");
        }
        return false;
    }

    private String extractTitle(WebElement card) {
        String[] selectors = {
                "h3.SubjectAndPriceStyles__SubjectsText-sc-18v5hu8-1",
                "h3.SubjectAndPriceStyles__SubjectsText-sc-18v5hu8-1.hEywcV",
                "h3",
                "[class*='title']",
                "[class*='subject']"
        };

        for (String selector : selectors) {
            try {
                WebElement element = card.findElement(By.cssSelector(selector));
                String title = element.getText().trim();
                if (!title.isEmpty()) return title;
            } catch (Exception e) {
                // continue
            }
        }
        return "";
    }

    private String extractPrice(WebElement card) {
        String[] selectors = {
                ".SubjectAndPriceStyles__PriceValue-sc-18v5hu8-5",
                ".SubjectAndPriceStyles__PriceValue-sc-18v5hu8-5.lfrrNh",
                "[class*='price']"
        };

        for (String selector : selectors) {
            try {
                WebElement element = card.findElement(By.cssSelector(selector));
                return cleanPrice(element.getText());
            } catch (Exception e) {
                // continue
            }
        }
        return "0";
    }

    private String extractDescription(WebElement card) {
        String[] selectors = {
                ".SnippetBodyStyles__MainInfo-sc-tnih0-6",
                "[class*='description']",
                "[class*='info']"
        };

        for (String selector : selectors) {
            try {
                WebElement element = card.findElement(By.cssSelector(selector));
                return element.getText();
            } catch (Exception e) {
                // continue
            }
        }
        return "";
    }

    private String extractCreationTime(WebElement card) {
        String[] timeSelectors = {
                ".Date__DateText-sc-e1f8oi-1",
                "[class*='date']",
                "[class*='time']",
                ".order-date",
                ".snippet-date"
        };

        for (String selector : timeSelectors) {
            try {
                WebElement timeElement = card.findElement(By.cssSelector(selector));
                return timeElement.getText().trim();
            } catch (Exception e) {
                // continue
            }
        }
        return "Неизвестно";
    }

    private String cleanPrice(String price) {
        return price == null ? "0" : price.replaceAll("[^0-9]", "").trim();
    }

}
