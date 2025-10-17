package org.example.profiruparser.responder;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ProfiResponder {

    public boolean respondToOrder(WebDriver existingDriver, String orderId, String message) {
        try {
            WebDriverWait wait = new WebDriverWait(existingDriver, Duration.ofSeconds(20));

            // Переходим на страницу заказа
            existingDriver.get("https://profi.ru/backoffice/order/" + orderId);

            // Ждем загрузки страницы
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("body")
            ));

            // Пробуем разные селекторы для кнопки отклика
            WebElement respondBtn = findRespondButton(existingDriver, wait);
            if (respondBtn == null) {
                System.err.println("Кнопка отклика не найдена для заказа " + orderId);
                return false;
            }

            // Проверяем, что кнопка активна и не заблокирована
            if (!respondBtn.isEnabled()) {
                System.err.println("Кнопка отклика заблокирована для заказа " + orderId);
                return false;
            }

            // Скроллим к кнопке и кликаем
            ((JavascriptExecutor) existingDriver).executeScript("arguments[0].scrollIntoView(true);", respondBtn);
            Thread.sleep(500);
            respondBtn.click();

            // Ждем появления формы отклика с разными селекторами
            WebElement textarea = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("textarea, [role='textbox'], [data-testid='response-text'], " +
                            "[class*='textarea'], [class*='text-area'], " +
                            "form textarea, .response-form textarea")
            ));

            textarea.clear();
            textarea.sendKeys(message);

            // Ищем кнопку отправки
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(., 'Отправить') or contains(., 'Откликнуться') or contains(., 'Отправить отклик')]")
            ));
            submitBtn.click();

            // Проверяем успех с разными вариантами
            try {
                wait.until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success-message, [class*='success'], [class*='Success']")),
                        ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("body"), "отклик отправлен"),
                        ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("body"), "отклик успешно"),
                        ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("textarea, [role='textbox']")),
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(".order-card, [class*='order']")) // Возврат на страницу заказа
                ));
            } catch (TimeoutException e) {
                // Если не дождались явного успеха, но форма исчезла - считаем успехом
                if (existingDriver.findElements(By.cssSelector("textarea, [role='textbox']")).isEmpty()) {
                    return true;
                }
                throw e;
            }

            return true;

        } catch (TimeoutException e) {
            System.err.println("Таймаут при отклике на заказ " + orderId + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Ошибка при отклике на заказ " + orderId + ": " + e.getMessage());
            return false;
        }
    }

    private WebElement findRespondButton(WebDriver driver, WebDriverWait wait) {
        // Пробуем разные селекторы по очереди
        List<By> selectors = List.of(
                // Основные селекторы из HTML
                By.xpath("//div[contains(@class, 'order-card-bid-action-bar')]//div[contains(text(), 'Откликнуться')]"),
                By.xpath("//a[contains(@class, 'backoffice-common-button') and contains(., 'Откликнуться')]"),
                By.xpath("//button[contains(., 'Откликнуться')]"),
                By.xpath("//div[contains(text(), 'Откликнуться')]"),
                // Дополнительные селекторы
                By.cssSelector("[class*='bid-action'], [class*='respond'], [class*='BidAction']"),
                By.xpath("//*[contains(text(), 'Откликнуться')]")
        );

        for (By selector : selectors) {
            try {
                WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                if (element.isDisplayed() && element.isEnabled()) {
                    return element;
                }
            } catch (TimeoutException e) {
                // Пробуем следующий селектор
                continue;
            }
        }

        return null;
    }

}