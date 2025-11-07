package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.parser.config.ParserConfig;
import org.example.profiruparser.parser.service.async.BrowserPool;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component("seleniumWebDriverManager")
public class WebDriverManager implements BrowserPool {

    private final BlockingQueue<WebDriver> browserPool = new LinkedBlockingQueue<>();
    private final int MAX_BROWSERS = 3;
    private final ParserConfig parserConfig;

    @Autowired
    public WebDriverManager(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
        initializeBrowserPool();
        Runtime.getRuntime().addShutdownHook(new Thread(this::forceQuitAllChromeProcesses));
    }

    private void initializeBrowserPool() {
        System.out.println("🔄 Initializing browser pool with " + MAX_BROWSERS + " browsers");
        for (int i = 0; i < MAX_BROWSERS; i++) {
            try {
                browserPool.offer(createNewBrowser());
                System.out.println("✅ Browser " + (i+1) + " added to pool");
            } catch (Exception e) {
                System.err.println("❌ Failed to create browser: " + e.getMessage());
            }
        }
        System.out.println("🎯 Browser pool ready: " + browserPool.size() + "/" + MAX_BROWSERS);
    }

    private WebDriver createNewBrowser() {
        io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--start-maximized",
                "--disable-blink-features=AutomationControlled",
                "--remote-allow-origins=*",
                "--disable-notifications",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-extensions"
        );

        if (System.getenv("INSIDE_DOCKER") != null) {
            options.addArguments("--headless");
            options.setBinary("/usr/bin/google-chrome-stable");
        } else {
            options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        }

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);

        driver.get("https://profi.ru/");                                             /*меняем на "умные" задержки*/
        System.out.println("🔥 Browser pre-warmed: " + driver.getTitle());

        return driver;
    }

    @Override
    public CompletableFuture<WebDriver> acquireBrowser() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                WebDriver browser = browserPool.poll(5, TimeUnit.SECONDS);
                if (browser != null) {
                    System.out.println("✅ Browser acquired from pool");
                    return browser;
                }
                throw new RuntimeException("No browsers available");
            } catch (InterruptedException e) {
                throw new RuntimeException("Browser acquisition interrupted", e);
            }
        });
    }

    @Override
    public void releaseBrowser(WebDriver browser) {
        try {
            /* 🔥 ОЧИЩАЕМ COOKIES ПЕРЕД ВОЗВРАТОМ В ПУЛ*/
            browser.manage().deleteAllCookies();

            /* Очищаем localStorage*/
            String currentUrl = browser.getCurrentUrl();
            if (!currentUrl.startsWith("data:")) {
                ((JavascriptExecutor) browser).executeScript("window.localStorage.clear();");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Cleanup failed: " + e.getMessage());
        }

        if (!browserPool.offer(browser)) {
            browser.quit();
            System.out.println("❌ Failed to return browser, quit it");
        }
    }

    @Override
    public int getAvailableBrowsersCount() {
        return browserPool.size();
    }

    @Override
    public int getTotalBrowsersCount() {
        return MAX_BROWSERS;
    }

    private void forceQuitAllChromeProcesses() {
        browserPool.forEach(WebDriver::quit);
        browserPool.clear();
        System.out.println("🧹 All browsers cleaned up");
    }

}