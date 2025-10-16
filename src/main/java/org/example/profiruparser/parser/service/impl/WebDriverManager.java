package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.parser.config.ParserConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component("seleniumWebDriverManager")
@Qualifier("seleniumWebDriverManager")
public class WebDriverManager {
    private WebDriver driver;
    private WebDriverWait wait;
    private final ParserConfig parserConfig;

    @Autowired
    public WebDriverManager(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    public WebDriver getDriver() {
        if (driver == null) {
            initDriver();
        }
        return driver;
    }

    public WebDriverWait getWait() {
        if (wait == null && driver != null) {
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        }
        return wait;
    }

    private void initDriver() {
        io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();

        System.setProperty("wdm.cachePath", parserConfig.getWebDriverCachePath());

        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--start-maximized",
                "--disable-blink-features=AutomationControlled",
                "--remote-allow-origins=*",
                "--disable-notifications",
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        );

        if (System.getenv("INSIDE_DOCKER") != null) {
            options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless");
            options.setBinary("/usr/bin/google-chrome");
        } else {
            options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);
    }

    public void quitDriver() {
        if (driver != null) {
            driver.quit();
            driver = null;
            wait = null;
        }
    }

    public boolean isDriverInitialized() {
        return driver != null;
    }

}