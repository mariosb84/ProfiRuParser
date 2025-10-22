package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service("parserSearchService")
@Qualifier("parserSearchService")
public class SearchService {

    @Value("${webDriverManagerGetDriverSecond}")
    private String webDriverManagerGetDriverSecond;

    @Value("${searchUrl}")
    private String searchUrl;

    private final WebDriverManager webDriverManager;

    @Autowired
    public SearchService(WebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    public List<ProfiOrder> searchOrders(String keyword, OrderExtractionService extractionService) throws Exception {
        try {
            return searchOrdersMain(keyword, extractionService);
        } catch (Exception e) {
            System.err.println("Main search failed, trying alternative: " + e.getMessage());
            return searchOrdersAlternative(keyword, extractionService);
        }
    }

    private List<ProfiOrder> searchOrdersMain(String keyword, OrderExtractionService extractionService) throws Exception {
        System.out.println("=== STARTING UI SEARCH FOR: '" + keyword + "' ===");

        /*webDriverManager.getDriver().get("https://profi.ru/backoffice/n.php");*/ /* меняем на @Value*/
        webDriverManager.getDriver().get(this.webDriverManagerGetDriverSecond);
        Thread.sleep(3000);

        WebElement searchButton = findSearchButton();
        ((JavascriptExecutor) webDriverManager.getDriver()).executeScript("arguments[0].click();", searchButton);
        Thread.sleep(2000);

        WebElement searchInput = webDriverManager.getDriver().findElement(By.cssSelector(
                "input[data-testid='fulltext_edit_mode_test_id'], #searchField-1, .SearchFieldStyles__SearchInput-sc-10dn6mx-6"
        ));

        boolean searchPerformed = false;
        try {
            WebElement historyItem = findSearchHistoryItem(keyword);
            historyItem.click();
            searchPerformed = true;
            System.out.println("✅ Search via history selection");
        } catch (Exception e) {
            System.out.println("History item not found, trying manual input...");
        }

        if (!searchPerformed) {
            searchInput.clear();
            Thread.sleep(500);
            searchInput.sendKeys(keyword);
            Thread.sleep(1000);
            searchInput.sendKeys(Keys.ENTER);
            System.out.println("✅ Search via manual input + Enter");
        }

        Thread.sleep(5000);
        waitForSearchResults();
        scrollPage();

        return extractionService.extractOrders(webDriverManager.getDriver(), keyword);
    }

    private List<ProfiOrder> searchOrdersAlternative(String keyword, OrderExtractionService extractionService) throws Exception {
        System.out.println("=== USING ALTERNATIVE SEARCH ===");

        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());

        /*String searchUrl = "https://profi.ru/backoffice/n.php?q=" + encodedKeyword;*/ /* меняем на @Value*/
        String searchUrl = this.searchUrl + encodedKeyword;

        webDriverManager.getDriver().get(searchUrl);
        Thread.sleep(8000);
        scrollPage();

        return extractionService.extractOrders(webDriverManager.getDriver(), keyword);
    }

    private WebElement findSearchHistoryItem(String keyword) {
        List<WebElement> historyItems = webDriverManager.getDriver().findElements(By.cssSelector(
                "[data-testid='suggest_view'] .CellStyles__Text-sc-4tqx95-4"
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
                        By.cssSelector("[class*='loading'], [class*='spinner']")).isEmpty();
                if (!isLoading) {
                    return;
                }
            } catch (Exception e) {
                /* ignore*/
            }
            Thread.sleep(1000);
        }
    }

    private WebElement findSearchButton() {
        String[] selectors = {
                "button[data-testid='fulltext_view_mode_test_id']",
                ".SearchFieldStyles__ViewStateBlock-sc-10dn6mx-4",
                "[class*='search'] button",
                "button[aria-label*='поиск']",
                "button[aria-label*='заказ']"
        };

        for (String selector : selectors) {
            try {
                WebElement element = webDriverManager.getDriver().findElement(By.cssSelector(selector));
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (Exception e) {
                /* continue*/
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
