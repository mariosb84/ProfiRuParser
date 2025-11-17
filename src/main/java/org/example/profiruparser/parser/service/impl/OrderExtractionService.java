package org.example.profiruparser.parser.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service("orderExtractionService")
@Qualifier("orderExtractionService")
@Slf4j
public class OrderExtractionService {

    @Value("${app.profi.selectors.order-cards}")
    private String orderCards;

    @Value("${app.profi.selectors.title-selectors}")
    private String titleSelectors;

    @Value("${app.profi.selectors.price-selectors}")
    private String priceSelectors;

    @Value("${app.profi.selectors.description-selectors}")
    private String descriptionSelectors;

    @Value("${app.profi.selectors.time-selectors}")
    private String timeSelectors;

    private final OrderSortingService orderSortingService;

    public OrderExtractionService() {
        this.orderSortingService = new OrderSortingService();
    }

    public List<ProfiOrder> extractOrders(WebDriver driver, String keyword) {
        List<WebElement> cards = driver.findElements(By.cssSelector(this.orderCards));
        log.info("Total cards to process: {}", cards.size());

        String lowerKeyword = keyword.toLowerCase();

        /* 🔥 ПАРАЛЛЕЛЬНЫЙ ПАРСИНГ вместо последовательного*/
        List<ProfiOrder> orders = cards.parallelStream()
                .map(card -> {
                    try {
                        return processCardParallel(card, lowerKeyword, driver);
                    } catch (Exception e) {
                        log.debug("Error processing card: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return orderSortingService.sortOrdersByDate(orders);
    }

    /* 🔥 ДОБАВИТЬ этот новый метод*/
    private ProfiOrder processCardParallel(WebElement card, String lowerKeyword, WebDriver driver) {
        try {
            /* Быстро скроллим без задержек*/
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior: 'auto', block: 'center'});", card);

            if (!card.isDisplayed()) return null;

            String title = extractTitle(card);
            if (title.isEmpty()) return null;

            String lowerTitle = title.toLowerCase();
            boolean matches = lowerTitle.contains(lowerKeyword) ||
                    matchesKeywordVariations(title, lowerKeyword);

            if (matches) {
                ProfiOrder order = new ProfiOrder();
                order.setId(card.getAttribute("id") != null ? card.getAttribute("id") : "id_" + System.currentTimeMillis());
                order.setTitle(title);
                order.setPrice(extractPrice(card));
                order.setDescription(extractDescription(card));
                order.setCreationTime(extractCreationTime(card));

                log.debug("✅ PARALLEL ADDED: {} | Time: {}", title, order.getCreationTime());
                return order;
            }
        } catch (Exception e) {
            log.debug("Parallel card processing failed: {}", e.getMessage());
        }
        return null;
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

 /*   private String extractTitle(WebElement card) {
                                                                                        *//*меняем на @Value*//*

        String[] selectors = this.titleSelectors.split(","); *//* РАЗБИВАЕМ ПО ЗАПЯТОЙ*//*

        for (String selector : selectors) {
            try {
                WebElement element = card.findElement(By.cssSelector(selector.trim()));

                *//*WebElement element = card.findElement(By.cssSelector(selector));*//*

                String title = element.getText().trim();
                if (!title.isEmpty()) return title;
            } catch (Exception e) {
                *//* continue*//*
            }
        }
        return "";
    }*/

    private String extractTitle(WebElement card) {
        /* Пробуем сначала весь селектор как есть (для сложных случаев)*/
        try {
            WebElement element = card.findElement(By.cssSelector(this.titleSelectors));
            String title = element.getText().trim();
            if (!title.isEmpty()) return title;
        } catch (Exception e) {
            /* Если не работает - пробуем разбить по запятой*/
            String[] selectors = this.titleSelectors.split(",");
            for (String selector : selectors) {
                try {
                    WebElement element = card.findElement(By.cssSelector(selector.trim()));
                    String title = element.getText().trim();
                    if (!title.isEmpty()) return title;
                } catch (Exception ex) {
                    /* continue*/
                }
            }
        }
        return "";
    }

 /*   private String extractPrice(WebElement card) {
                                                                              *//*меняем на @Value*//*

        String[] selectors = this.priceSelectors.split(","); *//* РАЗБИВАЕМ ПО ЗАПЯТОЙ*//*

        for (String selector : selectors) {
            try {

                *//*WebElement element = card.findElement(By.cssSelector(selector));*//*

                WebElement element = card.findElement(By.cssSelector(selector.trim()));

                *//*return cleanPrice(element.getText());*//*

                return cleanPrice(element.getText().trim()); *//* ДОБАВЬ .trim() ЗДЕСЬ*//*

            } catch (Exception e) {
                *//* continue*//*
            }
        }
        return "0";
    }*/

    private String extractPrice(WebElement card) {
        /* Пробуем сначала весь селектор как есть*/
        try {
            WebElement element = card.findElement(By.cssSelector(this.priceSelectors));
            return cleanPrice(element.getText().trim());
        } catch (Exception e) {
            /* Если не работает - пробуем разбить по запятой*/
            String[] selectors = this.priceSelectors.split(",");
            for (String selector : selectors) {
                try {
                    WebElement element = card.findElement(By.cssSelector(selector.trim()));
                    return cleanPrice(element.getText().trim());
                } catch (Exception ex) {
                    /* continue*/
                }
            }
        }
        return "0";
    }

   /* private String extractDescription(WebElement card) {
                                                                                    *//*меняем на @Value*//*

        String[] selectors = this.descriptionSelectors.split(","); *//* РАЗБИВАЕМ ПО ЗАПЯТОЙ*//*

        for (String selector : selectors) {
            try {

                *//*WebElement element = card.findElement(By.cssSelector(selector));*//*

                WebElement element = card.findElement(By.cssSelector(selector.trim()));

                *//*return element.getText();*//*

                return element.getText().trim(); *//* ДОБАВЬ .trim() ЗДЕСЬ*//*

            } catch (Exception e) {
                *//* continue*//*
            }
        }
        return "";
    }*/

    private String extractDescription(WebElement card) {
        /* Пробуем сначала весь селектор как есть*/
        try {
            WebElement element = card.findElement(By.cssSelector(this.descriptionSelectors));
            return element.getText().trim();
        } catch (Exception e) {
            /* Если не работает - пробуем разбить по запятой*/
            String[] selectors = this.descriptionSelectors.split(",");
            for (String selector : selectors) {
                try {
                    WebElement element = card.findElement(By.cssSelector(selector.trim()));
                    return element.getText().trim();
                } catch (Exception ex) {
                    /* continue*/
                }
            }
        }
        return "";
    }

 /*   private String extractCreationTime(WebElement card) {
                                                                                        *//*меняем на @Value*//*

        String[] timeSelectors = this.timeSelectors.split(","); *//* РАЗБИВАЕМ ПО ЗАПЯТОЙ*//*

        for (String selector : timeSelectors) {
            try {

                *//*WebElement timeElement = card.findElement(By.cssSelector(selector));*//*

                WebElement timeElement = card.findElement(By.cssSelector(selector.trim()));

                return timeElement.getText().trim();
            } catch (Exception e) {
                *//* continue*//*
            }
        }
        return "Неизвестно";
    }*/

    private String extractCreationTime(WebElement card) {
        /* Пробуем сначала весь селектор как есть*/
        try {
            WebElement timeElement = card.findElement(By.cssSelector(this.timeSelectors));
            return timeElement.getText().trim();
        } catch (Exception e) {
            /* Если не работает - пробуем разбить по запятой*/
            String[] timeSelectors = this.timeSelectors.split(",");
            for (String selector : timeSelectors) {
                try {
                    WebElement timeElement = card.findElement(By.cssSelector(selector.trim()));
                    return timeElement.getText().trim();
                } catch (Exception ex) {
                    /* continue*/
                }
            }
        }
        return "Неизвестно";
    }

    private String cleanPrice(String price) {
        return price == null ? "0" : price.replaceAll("[^0-9]", "").trim();
    }

}



