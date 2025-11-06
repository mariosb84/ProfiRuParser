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

    /*  СТАРЫЙ МЕТОД ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ */
    /*public void performLogin(String login, String password) throws LoginException {
        try {
            if (webDriverManager.isDriverInitialized()) {
                webDriverManager.quitDriver();
            }

            webDriverManager.getDriver();
            performFullLogin(login, password);

        } catch (Exception e) {
            throw new LoginException("Ошибка входа: " + e.getMessage());
        }
    }*/

    /*  СТАРАЯ РЕАЛИЗАЦИЯ ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ */
 /*   private void performFullLogin(String login, String password) throws Exception {
        System.out.println("=== STARTING LOGIN (LEGACY) ===");

        webDriverManager.getDriver().get(this.webDriverManagerGetDriver);
        Thread.sleep(5000);

        try {
            WebElement loginInputElement = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(this.loginInput)));

            System.out.println("Login form loaded");
            loginInputElement.clear();
            Thread.sleep(1000);
            loginInputElement.sendKeys(login);

            WebElement passwordInputElement = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(this.passwordInput)));

            passwordInputElement.clear();
            Thread.sleep(1000);
            passwordInputElement.sendKeys(password);

            WebElement submitButtonElement = webDriverManager.getWait().until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector(this.submitButton)));

            submitButtonElement.click();

            boolean loginSuccess = webDriverManager.getWait().until(d -> {
                String currentUrl = d.getCurrentUrl();
                return currentUrl.contains("n.php") ||
                        !d.findElements(By.cssSelector(this.authSuccess)).isEmpty() ||
                        d.findElements(By.cssSelector(this.authFailure)).isEmpty();
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
    }*/

}



/*
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

        */
/*webDriverManager.getDriver().get("https://profi.ru/backoffice/a.php");*//*
 */
/* меняем на @Value*//*

        webDriverManager.getDriver().get(this.webDriverManagerGetDriver);
        Thread.sleep(5000);

        try {
            WebElement loginInput = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            */
/*By.cssSelector("input.login-form__input-login")));*//*
 */
/*меняем на @Value*//*

                            By.cssSelector(this.loginInput)));

            System.out.println("Login form loaded");
            loginInput.clear();
            Thread.sleep(1000);
            loginInput.sendKeys(login);

            WebElement passwordInput = webDriverManager.getWait().until(
                    ExpectedConditions.visibilityOfElementLocated(
                            */
/*By.cssSelector("input.login-form__input-password")));*//*
 */
/*меняем на @Value*//*

                            By.cssSelector(this.passwordInput)));

            passwordInput.clear();
            Thread.sleep(1000);
            passwordInput.sendKeys(password);

            WebElement submitButton = webDriverManager.getWait().until(
                    ExpectedConditions.elementToBeClickable(
                           */
/* By.cssSelector("a.ButtonsContainer__SubmitButton-sc-1bmmrie-5, button[type='submit']")));*//*
 */
/*меняем на @Value*//*

                            By.cssSelector(this.submitButton)));

            submitButton.click();

            boolean loginSuccess = webDriverManager.getWait().until(d -> {
                String currentUrl = d.getCurrentUrl();
                return currentUrl.contains("n.php") ||
                        */
/*!d.findElements(By.cssSelector(".user-avatar, [class*='user'], [data-testid*='user']")).isEmpty() ||*//*
 */
/*меняем на @Value*//*

                        !d.findElements(By.cssSelector(this.authSuccess)).isEmpty() ||
                        */
/*d.findElements(By.cssSelector(".login-form, input.login-form__input-login")).isEmpty();*//*
 */
/*меняем на @Value*//*

                        d.findElements(By.cssSelector(this.authFailure)).isEmpty();
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
*/
