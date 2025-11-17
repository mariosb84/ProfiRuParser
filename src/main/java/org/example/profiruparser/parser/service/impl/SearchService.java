package org.example.profiruparser.parser.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;

@Slf4j
@Service("parserSearchService")
@Qualifier("parserSearchService")
public class SearchService {

    @Value("${webDriverManagerGetDriverSecond}")
    private String webDriverManagerGetDriverSecond;

    @Value("${searchUrl}")
    private String searchUrl;

    @Value("${app.profi.selectors.search-input}")
    private String searchInput;

    @Value("${app.profi.selectors.search-history}")
    private String searchHistory;

    @Value("${app.profi.selectors.loading-indicator}")
    private String loadingIndicator;

    @Value("${app.profi.selectors.search-button-selectors}")
    private String searchButtonSelectors;

    @Value("${app.profi.selectors.order-cards}")
    private String orderCards;

    /* 🔥 НОВЫЙ МЕТОД ДЛЯ АСИНХРОННОЙ АРХИТЕКТУРЫ - с браузером из пула */
    public List<ProfiOrder> searchOrdersWithBrowser(String keyword,
                                                    OrderExtractionService extractionService,
                                                    WebDriver browser) throws Exception {

        browser.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);

        log.info("🎯 Search with provided browser for: '{}'", keyword);

        try {
            return searchOrdersMainWithBrowser(keyword, extractionService, browser);
        } catch (Exception e) {
            log.error("Main search failed, trying alternative: {}", e.getMessage());
            return searchOrdersAlternativeWithBrowser(keyword, extractionService, browser);
        }
    }

    /* 🔥 ОСНОВНОЙ ПОИСК С ПЕРЕДАННЫМ БРАУЗЕРОМ */
    private List<ProfiOrder> searchOrdersMainWithBrowser(String keyword,
                                                         OrderExtractionService extractionService,
                                                         WebDriver browser) throws Exception {
        log.info("=== STARTING UI SEARCH WITH BROWSER FOR: '{}' ===", keyword);

        /* Используем переданный браузер */
        browser.get(this.webDriverManagerGetDriverSecond);
        /*Thread.sleep(3000);*/                                                           /*меняем на "умные" задержки*/
        WebDriverWait wait = createWait(browser, 15);
        wait.until(ExpectedConditions.urlContains(this.webDriverManagerGetDriverSecond));

        WebElement searchButton = findSearchButtonWithBrowser(browser);
        ((JavascriptExecutor) browser).executeScript("arguments[0].click();", searchButton);
        /*Thread.sleep(2000); */                                                       /*меняем на "умные" задержки*/
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(this.searchInput)));

        WebElement searchInputElement = browser.findElement(By.cssSelector(this.searchInput));

        log.info("Using manual input (history disabled)");

        /* Очистка поля */
        searchInputElement.clear();
        Thread.sleep(500);

        searchInputElement.sendKeys(Keys.CONTROL + "a");
        Thread.sleep(200);
        searchInputElement.sendKeys(Keys.DELETE);
        Thread.sleep(500);

        String currentText = searchInputElement.getAttribute("value");
        if (!currentText.isEmpty()) {
            log.warn("WARNING: Field not empty after clear: '{}'", currentText);
            searchInputElement.clear();
            Thread.sleep(500);
        }

        searchInputElement.sendKeys(keyword);
        Thread.sleep(1000);
        searchInputElement.sendKeys(Keys.ENTER);
        log.info("✅ Search via manual input + Enter");

        /*Thread.sleep(5000);*/                                                          /*меняем на "умные" задержки*/
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(this.loadingIndicator)));
        waitForSearchResultsWithBrowser(browser);
        scrollPageWithBrowser(browser);

        /* OrderExtractionService ожидает 2 аргумента - browser и keyword */
        return extractionService.extractOrders(browser, keyword);
    }

    /* 🔥 АЛЬТЕРНАТИВНЫЙ ПОИСК С ПЕРЕДАННЫМ БРАУЗЕРОМ */
    private List<ProfiOrder> searchOrdersAlternativeWithBrowser(
            String keyword,
            OrderExtractionService extractionService,
            WebDriver browser) throws Exception {
        log.info("=== USING ALTERNATIVE SEARCH WITH BROWSER ===");

        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
            String searchUrl = this.searchUrl + encodedKeyword;

            /* Используем переданный браузер */
            browser.get(searchUrl);
            /*Thread.sleep(8000);*/                                                       /*меняем на "умные" задержки*/
            WebDriverWait wait = createWait(browser, 15);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(this.orderCards)));
            scrollPageWithBrowser(browser);

            return extractionService.extractOrders(browser, keyword);

        } catch (Exception e) {
            log.error("Alternative search also failed: {}", e.getMessage());
            throw e;
        }
    }

    /* 🔥 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ С БРАУЗЕРОМ */

    private WebElement findSearchHistoryItemWithBrowser(String keyword, WebDriver browser) {
        List<WebElement> historyItems = browser.findElements(By.cssSelector(this.searchHistory));

        for (WebElement item : historyItems) {
            String itemText = item.getText().toLowerCase();
            if (itemText.contains(keyword.toLowerCase())) {
                return item;
            }
        }
        throw new NoSuchElementException("Search history item not found for: " + keyword);
    }

    private void waitForSearchResultsWithBrowser(WebDriver browser) {
        WebDriverWait wait = createWait(browser, 15);

        try {
            /* Умное ожидание: ждем пока пропадет индикатор загрузки ИЛИ появятся результаты*/
            wait.until(d -> {
                boolean isLoading = !d.findElements(By.cssSelector(this.loadingIndicator)).isEmpty();
                boolean hasResults = !d.findElements(By.cssSelector(this.orderCards)).isEmpty();
                return !isLoading || hasResults;
            });
            log.debug("✅ Search results loaded successfully");
        } catch (Exception e) {
            log.warn("⚠️ Search results wait timeout, continuing anyway: {}", e.getMessage());
        }
    }

    private WebElement findSearchButtonWithBrowser(WebDriver browser) {
        WebDriverWait wait = createWait(browser, 5); /* Только 5 секунд вместо 10*/

        String[] selectors = this.searchButtonSelectors.split(",");

        for (String selector : selectors) {
            try {
                /* Пробуем быстро найти без ожидания сначала*/
                WebElement element;
                try {
                    if (selector.startsWith("//")) {
                        element = browser.findElement(By.xpath(selector.trim()));
                    } else {
                        element = browser.findElement(By.cssSelector(selector.trim()));
                    }

                    if (element.isDisplayed() && element.isEnabled()) {
                        log.info("✅ Found search button instantly with selector: {}", selector);
                        return element;
                    }
                } catch (Exception e) {
                    /* Если не нашли быстро - ждем*/
                    if (selector.startsWith("//")) {
                        element = wait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath(selector.trim())));
                    } else {
                        element = wait.until(ExpectedConditions.elementToBeClickable(
                                By.cssSelector(selector.trim())));
                    }
                }

                return element;

            } catch (Exception e) {
                log.debug("Selector failed: {}", selector);
            }
        }
        throw new NoSuchElementException("Search button not found with any selector");
    }

    private void scrollPageWithBrowser(WebDriver browser) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) browser;
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Thread.sleep(2000);

        long newHeight = (long) js.executeScript("return document.body.scrollHeight");
        if (newHeight > lastHeight) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            Thread.sleep(1000);
        }
    }

    private WebDriverWait createWait(WebDriver browser, int seconds) {
        return new WebDriverWait(browser, Duration.ofSeconds(seconds));
    }

    /*  СТАРЫЕ МЕТОДЫ - ВРЕМЕННО ОСТАВЛЯЕМ ДЛЯ СОВМЕСТИМОСТИ */

    public List<ProfiOrder> searchOrders(String keyword, OrderExtractionService extractionService) throws Exception {
        /*  ЭТОТ МЕТОД УСТАРЕЛ - выбрасываем исключение */
        throw new UnsupportedOperationException(
                "❌ searchOrders() is DEPRECATED! " +
                        "Use searchOrdersWithBrowser() with browser from pool instead. " +
                        "Caller: " + Thread.currentThread().getStackTrace()[2]
        );
    }

    private List<ProfiOrder> searchOrdersMain(String keyword, OrderExtractionService extractionService) throws Exception {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    private List<ProfiOrder> searchOrdersAlternative(String keyword, OrderExtractionService extractionService) throws Exception {
        throw new UnsupportedOperationException("This method is deprecated");
    }

}
