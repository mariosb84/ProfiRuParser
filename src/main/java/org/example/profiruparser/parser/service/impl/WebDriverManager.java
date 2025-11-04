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
        Runtime.getRuntime().addShutdownHook(new Thread(this::forceQuitAllChromeProcesses));
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
        killChromeProcesses();

        io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
        System.setProperty("wdm.cachePath", parserConfig.getWebDriverCachePath());

        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--start-maximized",
                "--disable-blink-features=AutomationControlled",
                "--remote-allow-origins=*",
                "--disable-notifications",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu"
        );

        if (System.getenv("INSIDE_DOCKER") != null) {
            options.addArguments("--headless");
            options.setBinary("/usr/bin/google-chrome-stable");
        } else {
            options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);
    }

    public void quitDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                /* ignore*/
            } finally {
                driver = null;
                wait = null;
                killChromeProcesses();
            }
        }
    }

    private void killChromeProcesses() {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                Runtime.getRuntime().exec("taskkill /F /IM chrome.exe");
                Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe");
            } else {
                Runtime.getRuntime().exec("pkill -f chrome");
                Runtime.getRuntime().exec("pkill -f chromedriver");
                Runtime.getRuntime().exec("pkill -f google-chrome");
            }

            Thread.sleep(1000);
        } catch (Exception e) {
            /* ignore*/
        }
    }

    public void forceQuitAllChromeProcesses() {
        quitDriver();
        killChromeProcesses();
    }

    public boolean isDriverInitialized() {
        return driver != null;
    }

}
