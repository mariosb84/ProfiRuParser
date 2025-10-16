package org.example.profiruparser.parser.util;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.nio.file.Files;
import java.nio.file.Path;

public class DebugUtils {

    public static void saveFullPageInfo(WebDriver driver, String prefix) {
        try {
            String html = driver.getPageSource();
            Files.writeString(Path.of(prefix + "_page.html"), html);

            byte[] screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
            Files.write(Path.of(prefix + "_screenshot.png"), screenshot);

            Files.writeString(Path.of(prefix + "_url.txt"), driver.getCurrentUrl());

            System.out.println("Debug info saved: " + prefix);
        } catch (Exception e) {
            System.err.println("Error saving debug info: " + e.getMessage());
        }
    }

}