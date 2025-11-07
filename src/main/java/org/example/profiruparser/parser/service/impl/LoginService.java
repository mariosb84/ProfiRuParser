package org.example.profiruparser.parser.service.impl;

import lombok.Getter;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service("parserLoginService")
@Qualifier("parserLoginService")
public class LoginService {

    @Value("${webDriverManagerGetDriver}")
    private String webDriverManagerGetDriver;

    @Value("${app.profi.selectors.login-input}")
    private String loginInput;

    @Value("${app.profi.selectors.password-input}")
    private String passwordInput;

    @Value("${app.profi.selectors.submit-button}")
    private String submitButton;

    @Value("${app.profi.selectors.auth-success}")
    private String authSuccess;

    @Value("${app.profi.selectors.auth-failure}")
    private String authFailure;

    /* 🔥 WebDriverManager оставляем для обратной совместимости */
    private final WebDriverManager webDriverManager;
    @Getter
    private boolean loggedIn = false;

    @Autowired
    public LoginService(WebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    /* 🔥 НОВЫЙ МЕТОД ДЛЯ РАБОТЫ С БРАУЗЕРОМ ИЗ ПУЛА */
    public void performLoginWithBrowser(String login, String password, WebDriver browser) throws Exception {
        System.out.println("=== STARTING LOGIN WITH PROVIDED BROWSER ===");

        try {
            performFullLoginWithBrowser(login, password, browser);
        } catch (Exception e) {
            throw new LoginException("Ошибка входа: " + e.getMessage());
        }
    }

    /* 🔥 ОБНОВЛЕННЫЙ МЕТОД С БРАУЗЕРОМ ИЗ ПУЛА */
    private void performFullLoginWithBrowser(String login, String password, WebDriver browser) throws Exception {
        System.out.println("🔄 Using provided browser for login");

        /* 🎯 ИСПОЛЬЗУЕМ ПЕРЕДАННЫЙ БРАУЗЕР ИЗ ПУЛА */
        browser.get(this.webDriverManagerGetDriver);
        Thread.sleep(5000);

        try {
            /* 🔄 СОЗДАЕМ WebDriverWait ДЛЯ ПЕРЕДАННОГО БРАУЗЕРА */
            WebDriverWait customWait = new WebDriverWait(browser, Duration.ofSeconds(60));

            WebElement loginInputElement = customWait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(this.loginInput)));

            System.out.println("Login form loaded");
            loginInputElement.clear();
            Thread.sleep(1000);
            loginInputElement.sendKeys(login);

            WebElement passwordInputElement = customWait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(this.passwordInput)));

            passwordInputElement.clear();
            Thread.sleep(1000);
            passwordInputElement.sendKeys(password);

            WebElement submitButtonElement = customWait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector(this.submitButton)));

            submitButtonElement.click();

            /* 🔄 ОЖИДАНИЕ С ПЕРЕДАННЫМ БРАУЗЕРОМ */
            boolean loginSuccess = customWait.until(d -> {
                String currentUrl = d.getCurrentUrl();
                return currentUrl.contains("n.php") ||
                        !d.findElements(By.cssSelector(this.authSuccess)).isEmpty() ||
                        d.findElements(By.cssSelector(this.authFailure)).isEmpty();
            });

            if (loginSuccess) {
                loggedIn = true;
                System.out.println("✅ Login successful with provided browser!");
                Thread.sleep(3000);
            } else {
                throw new Exception("Login failed - still on login page");
            }

        } catch (TimeoutException e) {
            if (browser.getCurrentUrl().contains("n.php")) {
                loggedIn = true;
                System.out.println("✅ Already logged in with provided browser");
            } else {
                throw new Exception("Login timeout: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new Exception("Login failed: " + e.getMessage());
        }
    }

}



