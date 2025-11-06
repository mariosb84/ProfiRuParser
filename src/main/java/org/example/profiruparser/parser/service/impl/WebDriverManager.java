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

   /* @Override
    public void releaseBrowser(WebDriver browser) {
        try {
            browser.manage().deleteAllCookies();
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
    }*/

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



/*package org.example.profiruparser.parser.service.impl;

import org.example.profiruparser.parser.config.ParserConfig;
import org.example.profiruparser.parser.service.async.BrowserPool;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component("seleniumWebDriverManager")
@Qualifier("seleniumWebDriverManager")
public class WebDriverManager implements BrowserPool { *//* ⚡ Теперь реализуем BrowserPool для масштабирования *//*

    *//* СТАРЫЕ ПОЛЯ для обратной совместимости *//*
    private WebDriver driver;           *//* Главный браузер для старого кода *//*
    private WebDriverWait wait;         *//* Wait для старого кода *//*
    private final ParserConfig parserConfig;

    *//* 🔄 НОВЫЕ ПОЛЯ ДЛЯ ПУЛА БРАУЗЕРОВ *//*
    private final BlockingQueue<WebDriver> browserPool = new LinkedBlockingQueue<>(); *//* Очередь доступных браузеров *//*
    private final AtomicInteger activeBrowsers = new AtomicInteger(0); *//* Счетчик активных браузеров *//*
    private final int MAX_BROWSERS = 3; *//* Максимальное количество браузеров в пуле *//*

    @Autowired
    public WebDriverManager(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;

        *//* 🔄 Инициализируем пул браузеров при создании *//*
        initializeBrowserPool();

        *//* ✅ Очистка при завершении приложения *//*
        Runtime.getRuntime().addShutdownHook(new Thread(this::forceQuitAllChromeProcesses));
    }

    *//* 🔄 ИНИЦИАЛИЗАЦИЯ ПУЛА БРАУЗЕРОВ *//*
    private void initializeBrowserPool() {
        System.out.println("🔄 Initializing browser pool with " + MAX_BROWSERS + " browsers");

        for (int i = 0; i < MAX_BROWSERS; i++) {
            try {
                WebDriver browser = createNewBrowser();
                browserPool.offer(browser); *//* Добавляем браузер в пул *//*
                activeBrowsers.incrementAndGet(); *//* Увеличиваем счетчик *//*
                System.out.println("✅ Browser " + (i+1) + " added to pool");
            } catch (Exception e) {
                System.err.println("❌ Failed to create browser " + (i+1) + ": " + e.getMessage());
            }
        }

        System.out.println("🎯 Browser pool ready: " + browserPool.size() + "/" + MAX_BROWSERS + " browsers available");
    }

    *//* 🔄 СОЗДАНИЕ НОВОГО БРАУЗЕРА (вынесено в отдельный метод) *//*
    private WebDriver createNewBrowser() {
        *//* Сначала убиваем старые процессы *//*
        killChromeProcesses();

        *//* Настраиваем ChromeDriver *//*
        io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
        System.setProperty("wdm.cachePath", parserConfig.getWebDriverCachePath());

        *//* Настройки Chrome *//*
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--start-maximized",
                "--disable-blink-features=AutomationControlled", *//* Скрываем автоматизацию *//*
                "--remote-allow-origins=*",
                "--disable-notifications",
                "--no-sandbox",           *//* Важно для Docker *//*
                "--disable-dev-shm-usage", *//* Важно для Docker *//*
                "--disable-gpu",
                "--disable-extensions", *//* ⚡ ДОБАВИТЬ*//*
                "--disable-plugins",    *//* ⚡ ДОБАВИТЬ*//*
                "--disable-popup-blocking" *//* ⚡ ДОБАВИТЬ*//*
        );

        *//* Настройки для Docker или локальной среды *//*
        if (System.getenv("INSIDE_DOCKER") != null) {
            options.addArguments("--headless"); *//* Без GUI в Docker *//*
            options.setBinary("/usr/bin/google-chrome-stable");
        } else {
            options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        }

        *//* Создаем и настраиваем драйвер *//*
        WebDriver newDriver = new ChromeDriver(options);

        *//*newDriver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);*//*

        *//* ⚡ УВЕЛИЧИВАЕМ ТАЙМАУТЫ*//*
        newDriver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS); *//* ⚡ 30 секунд*//*
        newDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);  *//* ⚡ 10 секунд*//*

        return newDriver;
    }

    *//* === СТАРЫЕ МЕТОДЫ ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ === *//*

    *//* ✅ Старый метод getDriver() - для существующего кода *//*
    public WebDriver getDriver() {

        System.out.println("🔍 getDriver() called from:");
        new Exception("Stack trace").printStackTrace(); // ⚡ Покажет все вызовы

        if (driver == null) {
            initDriver();
        }
        return driver;
    }

    *//* ✅ Старый метод initDriver() - использует createNewBrowser() *//*
    private void initDriver() {
        driver = createNewBrowser();
    }

    *//* ✅ Старый метод getWait() *//*
    public WebDriverWait getWait() {
        if (wait == null && driver != null) {
            wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        }
        return wait;
    }

    *//* ✅ Старый метод quitDriver() - закрывает главный браузер *//*
    public void quitDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                *//* Игнорируем ошибки при закрытии *//*
            } finally {
                driver = null;
                wait = null;
                killChromeProcesses(); *//* Все равно чистим процессы *//*
            }
        }
    }

    *//* ✅ Старый метод isDriverInitialized() *//*
    public boolean isDriverInitialized() {
        return driver != null;
    }

    *//* === НОВЫЕ МЕТОДЫ ДЛЯ BROWSER POOL === *//*

    *//* 🔄 ПОЛУЧЕНИЕ БРАУЗЕРА ИЗ ПУЛА (асинхронно) *//*
    @Override
    public CompletableFuture<WebDriver> acquireBrowser() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("🔍 Acquiring browser from pool... Available: " + browserPool.size());

                *//* Пытаемся взять браузер из пула с таймаутом 5 секунд *//*
                WebDriver browser = browserPool.poll(5, TimeUnit.SECONDS);

                if (browser != null) {
                    System.out.println("✅ Browser acquired from pool");
                    return browser;
                }

                *//* Если пул пуст, но можно создать новый браузер *//*
                if (activeBrowsers.get() < MAX_BROWSERS) {
                    System.out.println("🆕 Creating new browser (pool empty)");
                    WebDriver newBrowser = createNewBrowser();
                    activeBrowsers.incrementAndGet();
                    return newBrowser;
                }

                *//* Если достигли лимита и нет доступных браузеров *//*
                throw new RuntimeException("No browsers available in pool. Active: " + activeBrowsers.get());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Browser acquisition interrupted", e);
            }
        });
    }

    *//* 🔄 ВОЗВРАТ БРАУЗЕРА В ПУЛ *//*
    @Override
    public void releaseBrowser(WebDriver browser) {
        System.out.println("🔄 Returning browser to pool");

        try {
            *//* Очищаем браузер перед возвратом в пул *//*
            browser.manage().deleteAllCookies();

            *//* ⚡ ПРОВЕРЯЕМ ЧТО МЫ НЕ НА data: URL ПЕРЕД ОЧИСТКОЙ localStorage*//*
            String currentUrl = browser.getCurrentUrl();
            if (!currentUrl.startsWith("data:")) {
                ((JavascriptExecutor) browser).executeScript("window.localStorage.clear();");
            }

            System.out.println("🧹 Browser cleaned up");
        } catch (Exception e) {
            System.out.println("⚠️ Browser cleanup failed: " + e.getMessage());
            *//* ⚡ НЕ ПРЕРЫВАЕМ ВЫПОЛНЕНИЕ ИЗ-ЗА ОШИБКИ ОЧИСТКИ*//*
        }

        *//* Пытаемся вернуть браузер в пул *//*
        if (browserPool.offer(browser)) {
            System.out.println("✅ Browser returned to pool. Available: " + browserPool.size());
        } else {
            System.out.println("❌ Failed to return browser to pool, quitting...");
            try {
                browser.quit();
            } catch (Exception e) {
                System.out.println("Error quitting browser: " + e.getMessage());
            }
            activeBrowsers.decrementAndGet();
        }
    }

    *//* 🔄 КОЛИЧЕСТВО ДОСТУПНЫХ БРАУЗЕРОВ *//*
    @Override
    public int getAvailableBrowsersCount() {
        return browserPool.size();
    }

    *//* 🔄 ОБЩЕЕ КОЛИЧЕСТВО БРАУЗЕРОВ *//*
    @Override
    public int getTotalBrowsersCount() {
        return activeBrowsers.get();
    }

    *//* === МЕТОДЫ ОЧИСТКИ ПРОЦЕССОВ === *//*

    *//* ✅ УБИЙСТВО CHROME ПРОЦЕССОВ (остается без изменений) *//*
    private void killChromeProcesses() {
        try {
            System.out.println("🔫 KILLING CHROME PROCESSES...");

            *//* Убиваем все chrome-процессы *//*
            Runtime.getRuntime().exec(new String[]{"pkill", "-9", "-f", "chrome"});
            Runtime.getRuntime().exec(new String[]{"pkill", "-9", "-f", "chromedriver"});
            Runtime.getRuntime().exec(new String[]{"pkill", "-9", "-f", "google-chrome"});

            *//* Дополнительные команды для очистки *//*
            Runtime.getRuntime().exec(new String[]{"killall", "-9", "chrome"});
            Runtime.getRuntime().exec(new String[]{"killall", "-9", "chromedriver"});

            Thread.sleep(3000); *//* Ждем завершения процессов *//*

            *//* Проверяем результат *//*
            Process check = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps aux | grep chrome | wc -l"});
            check.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(check.getInputStream()));
            String line = reader.readLine();
            System.out.println("✅ Chrome processes after cleanup: " + line);

        } catch (Exception e) {
            System.out.println("Kill error: " + e.getMessage());
        }
    }

    *//* ✅ ПРИНУДИТЕЛЬНАЯ ОЧИСТКА ВСЕХ ПРОЦЕССОВ *//*
    public void forceQuitAllChromeProcesses() {
        System.out.println("🚨 FORCE QUITTING ALL CHROME PROCESSES");

        *//* Закрываем все браузеры в пуле *//*
        for (WebDriver browser : browserPool) {
            try {
                browser.quit();
            } catch (Exception e) {
                System.out.println("Error quitting browser from pool: " + e.getMessage());
            }
        }
        browserPool.clear();
        activeBrowsers.set(0);

        *//* Закрываем главный браузер *//*
        quitDriver();

        *//* Убиваем процессы *//*
        killChromeProcesses();

        System.out.println("🎯 All chrome processes cleaned up");
    }

}*/

