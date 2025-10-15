package org.example.profiruparser.service;


import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProfiResponder {
    public boolean respondToOrder(String orderId, String message) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        try {
            driver.get("https://profi.ru/backoffice/order_response.php?id=" + orderId);

            // Ожидание и заполнение поля ответа
            WebElement textarea = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("textarea[name='response_text'], [data-test-id='response-text']")));
            textarea.sendKeys(message);

            // Клик по кнопке отправки
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[type='submit'], [data-test-id='submit-response']")));
            submitBtn.click();

            // Проверка успешной отправки
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success-message")),
                    ExpectedConditions.textToBePresentInElementLocated(
                            By.cssSelector("body"), "отклик отправлен")
            ));

            return true;
        } catch (TimeoutException e) {
            // Попытка определить причину ошибки
            if (!driver.findElements(By.cssSelector(".captcha-container")).isEmpty()) {
                System.err.println("Обнаружена CAPTCHA");
            }
            return false;
        } finally {
            driver.quit();
        }
    }

}
