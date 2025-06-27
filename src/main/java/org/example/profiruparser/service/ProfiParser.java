package org.example.profiruparser.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--start-maximized");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");

            // Явное указание пути к Chrome (проверьте путь на вашей системе!)
            options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");

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

        // Переходим на страницу поиска, даже если мы уже на ней

        driver.get("https://profi.ru/backoffice/n.php?query=" + searchQuery);

        // Новые локаторы для Profi.ru
        By orderCardSelector = By.cssSelector(".TaskCard_taskCard__uP7Hp, [data-test-id='task-card']");

        // Ожидание появления заказов
        wait.until(ExpectedConditions.presenceOfElementLocated(orderCardSelector));

        // Имитация человеческого скроллинга
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight / 3);");
        Thread.sleep(2000);
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight * 2/3);");
        Thread.sleep(2000);

        List<WebElement> items = driver.findElements(orderCardSelector);
        List<ProfiOrder> orders = new ArrayList<>();

        for (WebElement item : items) {
            try {
                String id = item.getAttribute("data-order-id") != null ?
                        item.getAttribute("data-order-id") :
                        item.getAttribute("data-test-id");

                String title = item.findElement(By.cssSelector(".TaskCard_title__Xq7jU, [data-test-id='task-title']")).getText();
                String price = item.findElement(By.cssSelector(".TaskCard_price__2lpcq, [data-test-id='task-price']")).getText();

                orders.add(new ProfiOrder(id, title, price));
            } catch (NoSuchElementException e) {
                System.err.println("Не удалось извлечь данные заказа: " + e.getMessage());
            }
        }

        return orders;
    }

    private void humanType(WebElement element, String text) throws InterruptedException {
        for (char c : text.toCharArray()) {
            element.sendKeys(String.valueOf(c));
            Thread.sleep(50 + (long)(Math.random() * 100));
        }
    }

    private void humanClick(WebElement element) throws InterruptedException {
        new Actions(driver)
                .moveToElement(element)
                .pause(Duration.ofMillis(500))
                .click()
                .perform();
        Thread.sleep(1000);
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }

}

