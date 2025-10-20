package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service("orderExtractionService")
@Qualifier("orderExtractionService")
public class OrderExtractionService {

    private final OrderSortingService orderSortingService;

    public OrderExtractionService() {
        this.orderSortingService = new OrderSortingService();
    }

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

        return orderSortingService.sortOrdersByDate(orders);
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
                /* continue*/
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
                /* continue*/
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
                /* continue*/
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
                /* continue*/
            }
        }
        return "Неизвестно";
    }

    private String cleanPrice(String price) {
        return price == null ? "0" : price.replaceAll("[^0-9]", "").trim();
    }

}
