package org.example.profiruparser.service;


import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ProfiResponder {

    public boolean respondToOrder(String orderId, String message) {
        ChromeOptions options = new ChromeOptions();
        /* options.addArguments("--headless"); - если нужен безголовый режим*/
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        try {
            String url = "https://profi.ru/backoffice/order_response.php?id=" + orderId;
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement textarea = wait.until(ExpectedConditions.elementToBeClickable(By.name("response_text")));
            textarea.sendKeys(message);

            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".submit-btn")));
            submitBtn.click();

            /* Можно добавить проверку успешной отправки (например, появление сообщения об успехе)*/
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            driver.quit();
        }
    }

}
