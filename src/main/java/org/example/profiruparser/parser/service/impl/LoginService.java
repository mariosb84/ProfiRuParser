package org.example.profiruparser.parser.service.impl;

import lombok.Getter;
import org.example.profiruparser.errors.LoginException;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("parserLoginService")
@Qualifier("parserLoginService")
public class LoginService {

    @Value("${webDriverManagerGetDriver}")
    private String webDriverManagerGetDriver;

    private final WebDriverManager webDriverManager;
    @Getter
    private boolean loggedIn = false;

    @Autowired
    public LoginService(WebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    public void performLogin(String login, String password) throws LoginException {
        try {
            if (webDriverManager.isDriverInitialized()) {
                webDriverManager.quitDriver();
            }

            webDriverManager.getDriver();
            performFullLogin(login, password);

        } catch (Exception e) {
            throw new LoginException("Ошибка входа: " + e.getMessage());
        }
    }

    private void performFullLogin(String login, String password) throws Exception {
        System.out.println("=== STARTING LOGIN ===");

        /*webDriverManager.getDriver().get("https://profi.ru/backoffice/a.php");*/ /* меняем на @Value*/
        webDriverManager.getDriver().get(this.webDriverManagerGetDriver);
        Thread.sleep(5000);

        try {
            WebElement loginInput = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("input.login-form__input-login")));

            System.out.println("Login form loaded");
            loginInput.clear();
            Thread.sleep(1000);
            loginInput.sendKeys(login);

            WebElement passwordInput = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("input.login-form__input-password")));

            passwordInput.clear();
            Thread.sleep(1000);
            passwordInput.sendKeys(password);

            WebElement submitButton = webDriverManager.getWait().until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("a.ButtonsContainer__SubmitButton-sc-1bmmrie-5, button[type='submit']")));

            submitButton.click();

            boolean loginSuccess = webDriverManager.getWait().until(d -> {
                String currentUrl = d.getCurrentUrl();
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
            if (webDriverManager.getDriver().getCurrentUrl().contains("n.php")) {
                loggedIn = true;
                System.out.println("✅ Already logged in");
            } else {
                throw new Exception("Login timeout: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new Exception("Login failed: " + e.getMessage());
        }
    }

}