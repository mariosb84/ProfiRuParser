package org.example.profiruparser.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.example.profiruparser.domain.dto.ProfiOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ProfiParser {

    private WebDriver driver;
    private WebDriverWait wait;
    private boolean loggedIn = false;

    public ProfiParser() {
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless"); // если нужен безголовый режим
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void login(String login, String password) throws Exception {
        driver.get("https://profi.ru/backoffice/a.php");

        WebElement loginInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("input.login-form__input-login")));
        loginInput.clear();
        loginInput.sendKeys(login);

        WebElement passwordInput = driver.findElement(By.cssSelector("input.login-form__input-password"));
        passwordInput.clear();
        passwordInput.sendKeys(password);

        WebElement submitButton = driver.findElement(By.cssSelector("a.ButtonsContainer__SubmitButton-sc-1bmmrie-5"));
        submitButton.click();

        // Ждём, что загрузятся заказы (или другой элемент, который появляется после входа)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".order-card")));

        loggedIn = true;
    }

    public List<ProfiOrder> parseOrders(String searchQuery) throws Exception {
        if (!loggedIn) {
            throw new IllegalStateException("Не выполнен вход. Сначала вызовите login()");
        }

        List<ProfiOrder> orders = new ArrayList<>();
        String url = "https://profi.ru/backoffice/n.php?query=" +
                URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);

        driver.get(url);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".order-card")));

        List<WebElement> items = driver.findElements(By.cssSelector(".order-card"));
        for (WebElement item : items) {
            try {
                String id = item.getAttribute("data-order-id");
                String title = item.findElement(By.cssSelector(".title")).getText();
                String price = item.findElement(By.cssSelector(".price")).getText();

                ProfiOrder order = new ProfiOrder(id, title, price);
                orders.add(order);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return orders;
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }

}

