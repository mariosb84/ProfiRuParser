package org.example.profiruparser.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Парсер заказов с сайта Profi.ru
 */
public class ProfiParser {
    private WebDriver driver;
    private WebDriverWait wait;
    private boolean loggedIn = false;

    /**
     * Инициализирует WebDriver с настройками
     */
    private void initDriver() {
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--start-maximized",
                    "--disable-blink-features=AutomationControlled",
                    "--remote-allow-origins=*",
                    "--disable-notifications",
                    "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            );

            if (System.getenv("INSIDE_DOCKER") != null) {
                options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless");
                options.setBinary("/usr/bin/google-chrome");
            } else {
                WebDriverManager.chromedriver().setup();
                options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
            }

            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        }
    }

    /**
     * Парсит заказы по ключевому слову
     */
    public List<ProfiOrder> parseOrders(String keyword) throws Exception {
        if (!loggedIn) {
            throw new IllegalStateException("Требуется авторизация");
        }

        try {
            return parseOrdersMain(keyword);
        } catch (Exception e) {
            System.err.println("Main search failed, trying alternative: " + e.getMessage());
            return parseOrdersAlternative(keyword);
        }
    }

    /**
     * Основной метод поиска через UI
     */
    private List<ProfiOrder> parseOrdersMain(String keyword) throws Exception {
        System.out.println("=== STARTING UI SEARCH FOR: '" + keyword + "' ===");

        driver.get("https://profi.ru/backoffice/n.php");
        Thread.sleep(3000);
        saveFullPageInfo("before_search");

        // 1. Кликаем на кнопку поиска - поле ввода СТАНЕТ активным
        WebElement searchButton = findSearchButton();
        System.out.println("Found search button, clicking...");
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchButton);
        Thread.sleep(2000);

        // 2. Поле ввода УЖЕ активно - используем его сразу
        WebElement searchInput = driver.findElement(By.cssSelector(
                "input[data-testid='fulltext_edit_mode_test_id'], #searchField-1, .SearchFieldStyles__SearchInput-sc-10dn6mx-6"
        ));
        System.out.println("Found active search input");

        // 3. Пробуем оба варианта поиска
        boolean searchPerformed = false;

        // Вариант A: Пробуем найти и кликнуть на элемент истории
        try {
            WebElement historyItem = findSearchHistoryItem(keyword);
            System.out.println("Found history item, clicking...");
            historyItem.click();
            searchPerformed = true;
            System.out.println("✅ Search via history selection");
        } catch (Exception e) {
            System.out.println("History item not found, trying manual input...");
        }

        // Вариант B: Если не нашли в истории, вводим вручную
        if (!searchPerformed) {
            System.out.println("Typing manually...");

            // Очищаем и вводим текст
            searchInput.clear();
            Thread.sleep(500);
            searchInput.sendKeys(keyword);
            Thread.sleep(1000);

            // Нажимаем Enter
            searchInput.sendKeys(Keys.ENTER);
            searchPerformed = true;
            System.out.println("✅ Search via manual input + Enter");
        }

        // 4. Ждем загрузки результатов
        System.out.println("Waiting for search results...");
        Thread.sleep(5000);
        waitForSearchResults();

        saveFullPageInfo("after_search");

        List<WebElement> cards = driver.findElements(By.cssSelector("a[data-testid$='_order-snippet']"));
        System.out.println("Total cards found: " + cards.size());

        scrollPage();
        return extractOrders(keyword);
    }

    /**
     * Ищет элемент в истории поиска по тексту
     */
    private WebElement findSearchHistoryItem(String keyword) {
        // Ищем элементы истории
        List<WebElement> historyItems = driver.findElements(By.cssSelector(
                "[data-testid='suggest_view'] .CellStyles__Text-sc-4tqx95-4"
        ));

        for (WebElement item : historyItems) {
            String itemText = item.getText().toLowerCase();
            if (itemText.contains(keyword.toLowerCase())) {
                System.out.println("Found history item: " + item.getText());
                return item;
            }
        }
        throw new NoSuchElementException("Search history item not found for: " + keyword);
    }

    /**
     * Ждет завершения поиска
     */
    private void waitForSearchResults() throws InterruptedException {
        // Ждем либо появления индикатора загрузки, либо его исчезновения
        for (int i = 0; i < 10; i++) {
            try {
                // Проверяем есть ли индикатор загрузки
                boolean isLoading = !driver.findElements(By.cssSelector("[class*='loading'], [class*='spinner']")).isEmpty();
                if (!isLoading) {
                    System.out.println("Search completed");
                    return;
                }
            } catch (Exception e) {
                // ignore
            }
            Thread.sleep(1000);
        }
        System.out.println("Search timeout, continuing...");
    }

    /**
     * Ищет кнопку поиска
     */
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
                WebElement element = driver.findElement(By.cssSelector(selector));
                if (element.isDisplayed()) {
                    System.out.println("Found search button with selector: " + selector);
                    return element;
                }
            } catch (Exception e) {
                // continue
            }
        }
        throw new NoSuchElementException("Search button not found");
    }

    /**
     * Альтернативный метод поиска через URL
     */
    private List<ProfiOrder> parseOrdersAlternative(String keyword) throws Exception {
        System.out.println("=== USING ALTERNATIVE SEARCH ===");

        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
        String searchUrl = "https://profi.ru/backoffice/n.php?q=" + encodedKeyword;

        System.out.println("Alternative search URL: " + searchUrl);
        driver.get(searchUrl);
        Thread.sleep(8000);

        saveFullPageInfo("alternative_search");

        List<WebElement> cards = driver.findElements(By.cssSelector("a[data-testid$='_order-snippet']"));
        System.out.println("Cards found with alternative search: " + cards.size());

        scrollPage();
        return extractOrders(keyword);
    }

    /**
     * Извлекает заказы со страницы
     */
    private List<ProfiOrder> extractOrders(String keyword) {
        List<ProfiOrder> orders = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        System.out.println("=== EXTRACTING ORDERS ===");
        System.out.println("Search keyword: " + keyword);

        List<WebElement> cards = driver.findElements(By.cssSelector("a[data-testid$='_order-snippet']"));
        System.out.println("Total cards to process: " + cards.size());

        for (int i = 0; i < cards.size(); i++) {
            try {
                WebElement card = cards.get(i);

                // Прокручиваем к карточке
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", card);
                Thread.sleep(100);

                if (!card.isDisplayed()) continue;

                String title = extractTitle(card);
                if (title.isEmpty()) continue;

                String lowerTitle = title.toLowerCase();
                boolean matches = lowerTitle.contains(lowerKeyword) ||
                        matchesKeywordVariations(title, keyword);

                System.out.println("Card " + i + ": '" + title + "' - matches: " + matches);

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

        System.out.println("=== EXTRACTION COMPLETE ===");
        System.out.println("Found " + orders.size() + " matching orders");

        // Сортируем заказы по дате (новые первыми)
        orders.sort((o1, o2) -> {
            try {
                long minutes1 = parseTimeToMinutes(o1.getCreationTime());
                long minutes2 = parseTimeToMinutes(o2.getCreationTime());

                // Для "вчера" и старых дат - инвертируем сортировку
                if (minutes1 > 24 * 60 && minutes2 > 24 * 60) {
                    // Оба заказа "вчера" или старше - сортируем по убыванию (новые первыми)
                    return Long.compare(minutes2, minutes1);
                } else {
                    // Обычные заказы (сегодня) - сортируем по возрастанию
                    return Long.compare(minutes1, minutes2);
                }
            } catch (Exception e) {
                return 0;
            }
        });

        System.out.println("=== EXTRACTION COMPLETE ===");
        System.out.println("Found " + orders.size() + " matching orders");
        return orders;
    }

    /**
     * Сравнивает строки времени для сортировки
     */
    private int compareTimeStrings(String time1, String time2) {
        // Приводим к минутам для сравнения
        long minutes1 = parseTimeToMinutes(time1);
        long minutes2 = parseTimeToMinutes(time2);
        return Long.compare(minutes1, minutes2);
    }

    /**
     * Парсит строку времени в минуты (для сортировки)
     */
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
            // Для "Вчера в HH:MM" - считаем как 24 часа + время
            return 24 * 60 + parseYesterdayTime(lowerTime);
        } else {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Парсит время для формата "Вчера в HH:MM"
     */
    private int parseYesterdayTime(String time) {
        try {
            // Извлекаем время типа "21:12"
            String timePart = time.replace("вчера в", "").trim();

            // Убираем возможные лишние символы
            timePart = timePart.replaceAll("[^0-9:]", "");

            String[] parts = timePart.split(":");
            if (parts.length >= 2) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60 + minutes;
            } else if (parts.length == 1) {
                // Если только часы без минут
                int hours = Integer.parseInt(parts[0]);
                return hours * 60;
            }
        } catch (Exception e) {
            System.err.println("Error parsing yesterday time: " + time);
        }
        return 0;
    }

    /**
     * Проверяет варианты ключевого слова
     */
    private boolean matchesKeywordVariations(String title, String keyword) {                                                 // нужен ли в extractOrders  ????
        String lowerTitle = title.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        if (lowerKeyword.equals("юрист")) {
            return lowerTitle.contains("юрист") ||
                    lowerTitle.contains("юридич") ||
                    lowerTitle.contains("юрид");
        }
        return false;
    }

    /**
     * Извлекает заголовок
     */
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

    /**
     * Извлекает цену
     */
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

    /**
     * Извлекает описание
     */
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

    /**
     * Прокручивает страницу
     */
    private void scrollPage() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Thread.sleep(2000);

        long newHeight = (long) js.executeScript("return document.body.scrollHeight");
        if (newHeight > lastHeight) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            Thread.sleep(1000);
        }
    }

    /**
     * Очищает цену
     */
    private String cleanPrice(String price) {
        return price == null ? "0" : price.replaceAll("[^0-9]", "").trim();
    }

    /**
     * Сохраняет отладочную информацию
     */
    private void saveFullPageInfo(String prefix) {
        try {
            String html = driver.getPageSource();
            Files.writeString(Path.of(prefix + "_page.html"), html);

            byte[] screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
            Files.write(Path.of(prefix + "_screenshot.png"), screenshot);

            Files.writeString(Path.of(prefix + "_url.txt"), driver.getCurrentUrl());

            System.out.println("Debug info saved: " + prefix);
            System.out.println("URL: " + driver.getCurrentUrl());
            System.out.println("HTML length: " + html.length());

        } catch (Exception e) {
            System.err.println("Error saving debug info: " + e.getMessage());
        }
    }

    /**
     * Закрывает браузер
     */
    public void close() {
        if (driver != null) {
            driver.quit();
            driver = null;
            loggedIn = false;
        }
    }

    /**
     * Гарантирует авторизацию
     */
    public void ensureLoggedIn(String login, String password) throws LoginException {
        try {
            if (driver != null) {
                driver.quit();
                driver = null;
            }
            initDriver();
            performFullLogin(login, password);

        } catch (Exception e) {
            throw new LoginException("Ошибка входа: " + e.getMessage());
        }
    }

    /**
     * Выполняет вход - УЛУЧШЕННАЯ ВЕРСИЯ
     */
    private void performFullLogin(String login, String password) throws Exception {
        System.out.println("=== STARTING LOGIN ===");

        driver.get("https://profi.ru/backoffice/a.php");
        Thread.sleep(5000);

        // Сохраняем страницу логина для отладки
        saveFullPageInfo("login_page");

        try {
            // Ждем загрузки формы логина
            WebElement loginInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input.login-form__input-login")));

            System.out.println("Login form loaded");

            // Очищаем и вводим логин
            loginInput.clear();
            Thread.sleep(1000);
            loginInput.sendKeys(login);
            System.out.println("Login entered");

            // Находим поле пароля
            WebElement passwordInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input.login-form__input-password")));

            passwordInput.clear();
            Thread.sleep(1000);
            passwordInput.sendKeys(password);
            System.out.println("Password entered");

            // Находим кнопку входа
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a.ButtonsContainer__SubmitButton-sc-1bmmrie-5, button[type='submit']")));

            System.out.println("Submit button found, clicking...");
            submitButton.click();

            // Ждем успешного входа - проверяем несколько условий
            System.out.println("Waiting for login completion...");

            boolean loginSuccess = wait.until(d -> {
                String currentUrl = d.getCurrentUrl();
                System.out.println("Current URL: " + currentUrl);

                // Успешный вход если:
                // 1. URL содержит n.php (страница заказов)
                // 2. Или есть элемент пользователя
                // 3. Или нет формы логина
                return currentUrl.contains("n.php") ||
                        !d.findElements(By.cssSelector(".user-avatar, [class*='user'], [data-testid*='user']")).isEmpty() ||
                        d.findElements(By.cssSelector(".login-form, input.login-form__input-login")).isEmpty();
            });

            if (loginSuccess) {
                loggedIn = true;
                System.out.println("✅ Login successful!");
                Thread.sleep(3000);
            } else {
                throw new Exception("Login failed - still on login page");
            }

        } catch (TimeoutException e) {
            // Проверяем если мы уже залогинены
            if (driver.getCurrentUrl().contains("n.php")) {
                loggedIn = true;
                System.out.println("✅ Already logged in");
            } else {
                saveFullPageInfo("login_error");
                throw new Exception("Login timeout: " + e.getMessage());
            }
        } catch (Exception e) {
            saveFullPageInfo("login_error");
            throw new Exception("Login failed: " + e.getMessage());
        }
    }

    /**
     * Извлекает время создания заказа
     */
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

}



