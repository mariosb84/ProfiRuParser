package org.example.profiruparser.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
/*import org.openqa.selenium.support.ui.ExpectedConditions;*/
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class SmartWaitService {

    public void waitForPageLoad(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(d -> ((JavascriptExecutor)d)
                            .executeScript("return document.readyState").equals("complete"));
        } catch (Exception e) {
            log.warn("Page load timeout, continuing anyway");
        }
    }

    public void waitForCookiesApplied(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> d.manage().getCookies().size() > 5);
        } catch (Exception e) {
            log.warn("Cookies check timeout, continuing anyway");
        }
    }

}
