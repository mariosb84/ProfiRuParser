package org.example.profiruparser.parser.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    /* УДАЛЯЕМ WebDriverManager - он нам больше не нужен */
    /* private final WebDriverManager webDriverManager; */

   /* @Autowired
    public SearchService() {
        *//* Конструктор без зависимостей - все браузеры приходят извне *//*
    }*/

    /* 🔥 НОВЫЙ МЕТОД ДЛЯ АСИНХРОННОЙ АРХИТЕКТУРЫ - с браузером из пула */
    public List<ProfiOrder> searchOrdersWithBrowser(String keyword, OrderExtractionService extractionService, WebDriver browser) throws Exception {

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
    private List<ProfiOrder> searchOrdersMainWithBrowser(String keyword, OrderExtractionService extractionService, WebDriver browser) throws Exception {
        log.info("=== STARTING UI SEARCH WITH BROWSER FOR: '{}' ===", keyword);

        /* Используем переданный браузер */
        browser.get(this.webDriverManagerGetDriverSecond);
        Thread.sleep(3000);

        WebElement searchButton = findSearchButtonWithBrowser(browser);
        ((JavascriptExecutor) browser).executeScript("arguments[0].click();", searchButton);
        Thread.sleep(2000);

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

        Thread.sleep(5000);
        waitForSearchResultsWithBrowser(browser);
        scrollPageWithBrowser(browser);

        /* OrderExtractionService ожидает 2 аргумента - browser и keyword */
        return extractionService.extractOrders(browser, keyword);
    }

    /* 🔥 АЛЬТЕРНАТИВНЫЙ ПОИСК С ПЕРЕДАННЫМ БРАУЗЕРОМ */
    private List<ProfiOrder> searchOrdersAlternativeWithBrowser(String keyword, OrderExtractionService extractionService, WebDriver browser) throws Exception {
        log.info("=== USING ALTERNATIVE SEARCH WITH BROWSER ===");

        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
            String searchUrl = this.searchUrl + encodedKeyword;

            /* Используем переданный браузер */
            browser.get(searchUrl);
            Thread.sleep(8000);
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

    private void waitForSearchResultsWithBrowser(WebDriver browser) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            try {
                boolean isLoading = !browser.findElements(By.cssSelector(this.loadingIndicator)).isEmpty();
                if (!isLoading) {
                    return;
                }
            } catch (Exception e) {
                /* ignore */
            }
            Thread.sleep(1000);
        }
    }

    private WebElement findSearchButtonWithBrowser(WebDriver browser) {
        String[] selectors = this.searchButtonSelectors.split(",");

        for (String selector : selectors) {
            try {
                WebElement element = browser.findElement(By.cssSelector(selector.trim()));
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (Exception e) {
                /* continue */
            }
        }
        throw new NoSuchElementException("Search button not found");
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

    /* 💀 СТАРЫЕ МЕТОДЫ - ВРЕМЕННО ОСТАВЛЯЕМ ДЛЯ СОВМЕСТИМОСТИ */

    public List<ProfiOrder> searchOrders(String keyword, OrderExtractionService extractionService) throws Exception {
        /* 💀 ЭТОТ МЕТОД УСТАРЕЛ - выбрасываем исключение */
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


/*
package org.example.profiruparser.parser.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j */
/** ДОБАВЛЯЕМ ЛОГГЕР *//*

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

    private final WebDriverManager webDriverManager;

    @Autowired
    public SearchService(WebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    public List<ProfiOrder> searchOrders(String keyword, OrderExtractionService extractionService) throws Exception {
        try {
            return searchOrdersMain(keyword, extractionService);
        } catch (Exception e) {
            log.error("Main search failed, trying alternative: {}", e.getMessage());

            */
/** ПЕРЕЗАПУСКАЕМ БРАУЗЕР ПЕРЕД АЛЬТЕРНАТИВНЫМ ПОИСКОМ *//*

           */
/* restartBrowser();*//*


            return searchOrdersAlternative(keyword, extractionService);
        }
    }

    private List<ProfiOrder> searchOrdersMain(String keyword, OrderExtractionService extractionService) throws Exception {
        log.info("=== STARTING UI SEARCH FOR: '{}' ===", keyword);

        */
/*webDriverManager.getDriver().get("https://profi.ru/backoffice/n.php");*//*
   */
/* меняем на @Value*//*

        webDriverManager.getDriver().get(this.webDriverManagerGetDriverSecond);
        Thread.sleep(3000);

        WebElement searchButton = findSearchButton();
        ((JavascriptExecutor) webDriverManager.getDriver()).executeScript("arguments[0].click();", searchButton);
        Thread.sleep(2000);

        WebElement searchInput = webDriverManager.getDriver().findElement(By.cssSelector(
                */
/*"input[data-testid='fulltext_edit_mode_test_id'], #searchField-1, .SearchFieldStyles__SearchInput-sc-10dn6mx-6"*//*
 */
/* меняем на @Value*//*

                this.searchInput
        ));

        */
/* ВСЕГДА ИСПОЛЬЗУЕМ РУЧНОЙ ВВОД (ИСТОРИЯ ОТКЛЮЧЕНА)*//*

        log.info("Using manual input (history disabled)");

        */
/* УСИЛЕННАЯ ОЧИСТКА ПОЛЯ*//*

        searchInput.clear();
        Thread.sleep(500);

        */
/* Дополнительная очистка через Ctrl+A + Delete*//*

        searchInput.sendKeys(Keys.CONTROL + "a");
        Thread.sleep(200);
        searchInput.sendKeys(Keys.DELETE);
        Thread.sleep(500);

        */
/* Проверка что поле пустое*//*

        String currentText = searchInput.getAttribute("value");
        if (!currentText.isEmpty()) {
            log.warn("WARNING: Field not empty after clear: '{}'", currentText);
            */
/* Повторная очистка*//*

            searchInput.clear();
            Thread.sleep(500);
        }

        searchInput.sendKeys(keyword);
        Thread.sleep(1000);
        searchInput.sendKeys(Keys.ENTER);
        log.info("✅ Search via manual input + Enter");

        Thread.sleep(5000);
        waitForSearchResults();
        scrollPage();

        return extractionService.extractOrders(webDriverManager.getDriver(), keyword);
    }

    private List<ProfiOrder> searchOrdersAlternative(String keyword, OrderExtractionService extractionService) throws Exception {
        log.info("=== USING ALTERNATIVE SEARCH ===");

        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());

            */
/*String searchUrl = "https://profi.ru/backoffice/n.php?q=" + encodedKeyword;*//*
 */
/* меняем на @Value*//*

            String searchUrl = this.searchUrl + encodedKeyword;

            */
/** УБЕДИТЕСЬ ЧТО БРАУЗЕР ПЕРЕСОЗДАН ПОСЛЕ quitDriver() *//*

            if (webDriverManager.getDriver() == null) {
                webDriverManager.getDriver(); */
/** пересоздаем драйвер *//*

            }

            webDriverManager.getDriver().get(searchUrl);
            Thread.sleep(8000);
            scrollPage();

            return extractionService.extractOrders(webDriverManager.getDriver(), keyword);

        } catch (Exception e) {
            log.error("Alternative search also failed: {}", e.getMessage());
            */
/** ПРИ ОШИБКЕ В АЛЬТЕРНАТИВНОМ ПОИСКЕ - ПЕРЕЗАПУСКАЕМ БРАУЗЕР *//*

            */
/*restartBrowser();*//*

            throw e;
        }
    }

    */
/** НОВЫЙ МЕТОД: ПЕРЕЗАПУСК БРАУЗЕРА ПРИ ОШИБКАХ *//*

 */
/*   private void restartBrowser() {
        try {
            log.info("Restarting browser...");
            webDriverManager.quitDriver();
            Thread.sleep(2000);
            *//*
*/
/** Браузер автоматически пересоздастся при следующем getDriver() *//*
*/
/*
        } catch (Exception ex) {
            log.error("Error during browser restart: {}", ex.getMessage());
        }
    }*//*


    private WebElement findSearchHistoryItem(String keyword) {
        List<WebElement> historyItems = webDriverManager.getDriver().findElements(By.cssSelector(
                */
/*"[data-testid='suggest_view'] .CellStyles__Text-sc-4tqx95-4"*//*
     */
/* меняем на @Value*//*

                this.searchHistory
        ));

        for (WebElement item : historyItems) {
            String itemText = item.getText().toLowerCase();
            if (itemText.contains(keyword.toLowerCase())) {
                return item;
            }
        }
        throw new NoSuchElementException("Search history item not found for: " + keyword);
    }

    private void waitForSearchResults() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            try {
                boolean isLoading = !webDriverManager.getDriver().findElements(
                        */
/*By.cssSelector("[class*='loading'], [class*='spinner']")).isEmpty();*//*
      */
/* меняем на @Value*//*

                        By.cssSelector(this.loadingIndicator)).isEmpty();
                if (!isLoading) {
                    return;
                }
            } catch (Exception e) {
                */
/* ignore*//*

            }
            Thread.sleep(1000);
        }
    }

    private WebElement findSearchButton() {
        String[] selectors = {
                */
/* меняем на @Value*//*

                */
/*"button[data-testid='fulltext_view_mode_test_id']",
                ".SearchFieldStyles__ViewStateBlock-sc-10dn6mx-4",
                "[class*='search'] button",
                "button[aria-label*='поиск']",
                "button[aria-label*='заказ']"*//*

                this.searchButtonSelectors
        };

        for (String selector : selectors) {
            try {
                WebElement element = webDriverManager.getDriver().findElement(By.cssSelector(selector));
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (Exception e) {
                */
/* continue*//*

            }
        }
        throw new NoSuchElementException("Search button not found");
    }

    private void scrollPage() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) webDriverManager.getDriver();
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Thread.sleep(2000);

        long newHeight = (long) js.executeScript("return document.body.scrollHeight");
        if (newHeight > lastHeight) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            Thread.sleep(1000);
        }
    }

}



*/
