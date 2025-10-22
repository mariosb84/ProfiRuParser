package org.example.profiruparser.responder;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ProfiResponder {

    @Value("${orderUrlSecond}")
    private String orderUrlSecond;

    public boolean respondToOrder(WebDriver existingDriver, String orderId, String message) {
        try {
            WebDriverWait wait = new WebDriverWait(existingDriver, Duration.ofSeconds(20));

            /* Переходим на страницу заказа*/

            /* ИСПРАВЛЕННЫЙ URL - используем n.php?o= вместо order*/

            /*String orderUrl = "https://profi.ru/backoffice/n.php?o=" + orderId;*/ /* меняем на @Value*/
            String orderUrl = this.orderUrlSecond + orderId;
            existingDriver.get(orderUrl);

            /* Ждем загрузки страницы*/
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("body")
            ));

            /* Пробуем разные селекторы для кнопки отклика*/
            WebElement respondBtn = findRespondButton(existingDriver, wait);
            if (respondBtn == null) {
                System.err.println("Кнопка отклика не найдена для заказа " + orderId);
                return false;
            }

            /* Проверяем, что кнопка активна и не заблокирована*/
            if (!respondBtn.isEnabled()) {
                System.err.println("Кнопка отклика заблокирована для заказа " + orderId);
                return false;
            }

            /* Скроллим к кнопке и кликаем*/
            ((JavascriptExecutor) existingDriver).executeScript("arguments[0].scrollIntoView(true);", respondBtn);
            Thread.sleep(500);
            respondBtn.click();

            /* Ждем появления формы отклика с разными селекторами*/
            WebElement textarea = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("textarea, [role='textbox'], [data-testid='response-text'], " +
                            "[class*='textarea'], [class*='text-area'], " +
                            "form textarea, .response-form textarea")
            ));

            textarea.clear();
            textarea.sendKeys(message);

            /* Ищем кнопку отправки*/
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(., 'Отправить') or contains(., 'Откликнуться') or contains(., 'Отправить отклик')]")
            ));
            submitBtn.click();

            /* Проверяем успех с разными вариантами*/
            try {
                wait.until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success-message, [class*='success'], [class*='Success']")),
                        ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("body"), "отклик отправлен"),
                        ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("body"), "отклик успешно"),
                        ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("textarea, [role='textbox']")),
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(".order-card, [class*='order']")) // Возврат на страницу заказа
                ));
            } catch (TimeoutException e) {
                /* Если не дождались явного успеха, но форма исчезла - считаем успехом*/
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
        /* Пробуем разные селекторы по очереди*/
        List<By> selectors = List.of(
                /* Основной селектор по структуре из HTML*/
                By.xpath("//a[contains(@class, 'backoffice-common-button') and contains(@class, 'order-card-bid-action-bar__button')]"),

                /* Селектор по тексту внутри кнопки*/
                By.xpath("//a[.//p[contains(text(), 'Откликнуться')]]"),
                By.xpath("//a[.//*[contains(text(), 'Откликнуться')]]"),

                /* Селекторы по классам*/
                By.cssSelector("a.backoffice-common-button.order-card-bid-action-bar__button"),
                By.cssSelector("[class*='order-card-bid-action-bar'] a[class*='backoffice-common-button']"),

                /* Универсальные селекторы*/
                By.xpath("//a[contains(@class, 'backoffice-common-button')]"),
                By.xpath("//*[contains(@class, 'order-card-bid-action-bar')]//a")
        );

        for (By selector : selectors) {
            try {
                System.out.println("Пробуем селектор: " + selector);
                WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                if (element.isDisplayed() && element.isEnabled()) {
                    System.out.println("Кнопка найдена с селектором: " + selector);
                    return element;
                }
            } catch (TimeoutException e) {
                System.out.println("Селектор не сработал: " + selector);
                continue;
            }
        }

        /* ДЕБАГ: выведем всю страницу для анализа*/
        System.out.println("ДЕБАГ - HTML страницы:");
        System.out.println(driver.getPageSource());

        return null;
    }

}
