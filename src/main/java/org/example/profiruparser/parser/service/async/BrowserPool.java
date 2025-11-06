package org.example.profiruparser.parser.service.async;

import org.openqa.selenium.WebDriver;
import java.util.concurrent.CompletableFuture;

public interface BrowserPool {
    CompletableFuture<WebDriver> acquireBrowser();
    void releaseBrowser(WebDriver browser);
    int getAvailableBrowsersCount();
    int getTotalBrowsersCount();
}