package org.example.profiruparser.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfiParser {
    private WebDriver driver;
    private WebDriverWait wait;
    private boolean loggedIn = false;

    public ProfiParser() {
        // Инициализация драйвера вынесена из конструктора

    }

    private void initDriver() {
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--start-maximized",
                    "--disable-blink-features=AutomationControlled",
                    "--remote-allow-origins=*",
                    "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            );

            if (System.getenv("INSIDE_DOCKER") != null) {
                // Настройки для Docker
                options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless");
                options.setBinary("/usr/bin/google-chrome");
            } else {
                // Настройки для локального запуска
                WebDriverManager.chromedriver().setup();
                options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
            }

            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        }
    }

    public void login(String login, String password) throws Exception {
        initDriver();
        String loginUrl = "https://profi.ru/backoffice/a.php";
        driver.get(loginUrl);

        try {
            // Проверка капчи
            if (isCaptchaPresent()) {
                handleCaptcha();
            }

            // Ввод логина
            WebElement loginInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input.login-form__input-login")));
            humanType(loginInput, login);

            // Ввод пароля
            WebElement passwordInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input.login-form__input-password")));
            humanType(passwordInput, password);

            // Проверка капчи после ввода
            if (isCaptchaPresent()) {
                handleCaptcha();
            }

            // Клик по кнопке входа
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a.ButtonsContainer__SubmitButton-sc-1bmmrie-5")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitButton);

            // Ожидание успешного входа по изменению контента страницы
            wait.until(ExpectedConditions.or(
                    // Вариант 1: Появление аватара пользователя
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".user-avatar")),

                    // Вариант 2: Исчезновение формы входа
                    ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".login-form")),

                    // Вариант 3: Появление элементов главной страницы
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(".search-form, .order-card, .TaskCard_taskCard__uP7Hp"))
            ));

            // Дополнительная проверка: если URL не изменился, но контент обновился
            if (driver.getCurrentUrl().equals(loginUrl)) {
                System.out.println("URL не изменился, но контент страницы обновлен. Авторизация успешна.");
            }

            loggedIn = true;

            // Закрываем возможные всплывающие окна
            closeWelcomePopupIfPresent();

            // Переходим на вкладку заказов
            navigateToOrdersPage();

            // Дополнительная проверка
            if (!isOrdersPageActive()) {
                throw new Exception("Вкладка 'Заказы' не стала активной после перехода");
            }

        } catch (Exception e) {
            saveDebugInfo("login_error");
            throw e;
        }
    }

    private WebElement findLoginButton() {
        try {
            // Попытка 1: По тексту кнопки
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(., 'Войти') or contains(., 'Sign in')]")));
        } catch (TimeoutException e) {
            try {
                // Попытка 2: По классу
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button[type='submit'], input[type='submit']")));
            } catch (TimeoutException ex) {
                // Попытка 3: По ID или другим атрибутам
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("#login-button, [data-test-id='login-button']")));
            }
        }
    }

    private boolean isCaptchaPresent() {
        try {
            return !driver.findElements(By.cssSelector(".captcha-container, iframe[src*='captcha']")).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void handleCaptcha() throws InterruptedException {
        System.out.println("Обнаружена CAPTCHA! Требуется ручной ввод");
        // Здесь можно добавить уведомление в Telegram
        sendTelegramAlert("Обнаружена CAPTCHA! Требуется ручной ввод");

        // Ожидание 2 минуты для ручного ввода
        Thread.sleep(120000);

        // Проверка, исчезла ли капча
        if (isCaptchaPresent()) {
            throw new RuntimeException("CAPTCHA не была решена в течение 2 минут");
        }
    }

    private void saveDebugInfo(String prefix) {
        try {
            // Сохранение HTML страницы
            String pageSource = driver.getPageSource();
            Files.write(Path.of(prefix + "_page.html"), pageSource.getBytes());

            // Сохранение скриншота
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(Path.of(prefix + "_screenshot.png"), screenshot);
        } catch (Exception e) {
            System.err.println("Не удалось сохранить отладочную информацию: " + e.getMessage());
        }
    }

    private void sendTelegramAlert(String message) {
        // Реализуйте отправку сообщения в Telegram
        // Это можно сделать через ваш ProfiBot или отдельно
        System.out.println("ALERT: " + message);
    }

    public List<ProfiOrder> parseOrders(String searchQuery) throws Exception {
        if (!loggedIn) {
            throw new IllegalStateException("Не выполнен вход. Сначала вызовите login()");
        }

        // Если мы уже на странице заказов, не нужно переходить снова
        if (!isOrdersPageActive()) {
            navigateToOrdersPage();
        }

        // Если есть поисковый запрос, добавляем его к URL
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            driver.get("https://profi.ru/backoffice/n.php?query=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8));
        }

        // Новые локаторы для Profi.ru
        By orderCardSelector = By.cssSelector("a[id^='7'][data-testid$='order-snippet']");
        By titleSelector = By.cssSelector(".SubjectAndPriceStyles__SubjectsText-sc-18v5hu8-1");
        By priceSelector = By.cssSelector(".SubjectAndPriceStyles__PriceValue-sc-18v5hu8-5");

        // Ожидание появления хотя бы одного заказа
        wait.until(ExpectedConditions.presenceOfElementLocated(orderCardSelector));

        // Имитация человеческого поведения
        humanScroll();

        List<WebElement> items = driver.findElements(orderCardSelector);
        List<ProfiOrder> orders = new ArrayList<>();

        for (WebElement item : items) {
            try {
                String id = item.getAttribute("id");
                String title = item.findElement(titleSelector).getText();
                String price = item.findElement(priceSelector).getText();


                // Фильтрация по ключевым словам (если searchQuery задан)
                if (searchQuery == null || searchQuery.isEmpty() ||
                        title.toLowerCase().contains(searchQuery.toLowerCase())) {
                    orders.add(new ProfiOrder(id, title, price));
                }
                //orders.add(new ProfiOrder(id, title, price));

            } catch (NoSuchElementException e) {
                System.err.println("Не удалось извлечь данные заказа: " + e.getMessage());
            }
        }

        return orders;
    }

    private void humanScroll() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (Long) js.executeScript("return document.body.scrollHeight");

        // Плавный скроллинг в несколько этапов
        for (int i = 1; i <= 3; i++) {
            js.executeScript("window.scrollTo(0, " + (height * i / 3) + ")");
            Thread.sleep(1000 + (long)(Math.random() * 1000));
        }
    }

    private void humanType(WebElement element, String text) throws InterruptedException {
        for (char c : text.toCharArray()) {
            element.sendKeys(String.valueOf(c));
            Thread.sleep(50 + (long)(Math.random() * 100));
        }
    }

    private void humanClick(WebElement element) throws InterruptedException {
        try {
            // Плавное перемещение к элементу
            new Actions(driver)
                    .moveToElement(element)
                    .pause(Duration.ofMillis(200))
                    .perform();

            // Небольшая случайная задержка перед кликом
            Thread.sleep(200 + (long)(Math.random() * 300));

            // Клик через JavaScript для надежности
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);

            // Задержка после клика
            Thread.sleep(400 + (long)(Math.random() * 500));
        } catch (StaleElementReferenceException e) {
            // Элемент мог устареть - попробуем найти снова и кликнуть
            System.out.println("Элемент устарел, повторяем клик...");
            WebElement refreshedElement = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath(".//ancestor-or-self::*[local-name()='a' or local-name()='button']")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", refreshedElement);
        }
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void navigateToOrdersPage() throws Exception {
        try {
            // Локатор для кнопки "Заказы" в мобильном меню
            WebElement ordersTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(@href, '/backoffice/n.php')]//div[contains(text(), 'Заказы')]")
            ));

            // Клик с человеческим поведением
            humanClick(ordersTab);

            // Ожидание загрузки страницы заказов (проверяем по заголовку или первому заказу)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//h1[contains(text(), 'Заказы')]")),
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("[id^='7']")) // ID заказов начинаются с 7
            ));

            System.out.println("Успешно перешли на вкладку 'Заказы'");
        } catch (TimeoutException e) {
            saveDebugInfo("orders_tab_error");
            throw new Exception("Не удалось найти или кликнуть вкладку 'Заказы'", e);
        }
    }

    private void closeWelcomePopupIfPresent() {
        try {
            // Попробуем найти кнопку закрытия по разным локаторам
            List<By> closeButtons = Arrays.asList(
                    By.cssSelector(".Modal__closeButton"),
                    By.cssSelector("[data-test-id='welcome-close']"),
                    By.cssSelector("button[aria-label='Закрыть']"),
                    By.xpath("//button[contains(., 'Закрыть')]")
            );

            for (By locator : closeButtons) {
                try {
                    WebElement closeButton = wait.until(ExpectedConditions.elementToBeClickable(locator));
                    humanClick(closeButton);
                    System.out.println("Закрыли приветственное окно");
                    Thread.sleep(1000); // Даем окну время закрыться
                    return; // Выходим после успешного закрытия
                } catch (TimeoutException | NoSuchElementException ignored) {
                    // Пробуем следующий локатор
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при закрытии приветственного окна: " + e.getMessage());
        }
    }

    private boolean isOrdersPageActive() {
        try {
            // Проверяем активное состояние вкладки по классу или атрибуту
            return wait.until(ExpectedConditions.attributeContains(
                    By.xpath("//a[contains(@href, '/backoffice/n.php')]"),
                    "class",
                    "active"
            ));
        } catch (TimeoutException e) {
            // Альтернативная проверка по URL
            return driver.getCurrentUrl().contains("/backoffice/n.php");
        }
    }

    /*не используем*/
    public void fillPassportData(
            String passportNumber,
            String passportIssueDate,
            String passportIssuedBy
    ) throws Exception {
        try {
            // Переход на страницу редактирования профиля
            driver.get("https://profi.ru/backoffice/a.php");

            // Открытие раздела с паспортными данными
            WebElement passportSection = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//div[contains(text(), 'Паспортные данные')]")
            ));
            passportSection.click();

            // Заполнение полей
            WebElement numberInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input[name='passport_number']")
            ));
            numberInput.sendKeys(passportNumber);

            WebElement dateInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input[name='passport_issue_date']")
            ));
            dateInput.sendKeys(passportIssueDate);

            WebElement issuedByInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input[name='passport_issued_by']")
            ));
            issuedByInput.sendKeys(passportIssuedBy);

            // Сохранение
            WebElement saveButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[type='submit']")
            ));
            saveButton.click();

            // Проверка успешного сохранения
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(".alert-success")
            ));
        } catch (Exception e) {
            saveDebugInfo("passport_error");
            throw e;
        }
    }

}

