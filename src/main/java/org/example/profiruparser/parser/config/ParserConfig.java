package org.example.profiruparser.parser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParserConfig {

    @Value("${webdrivermanager.cache.path:./drivers}")
    private String webDriverCachePath;

    public String getWebDriverCachePath() {
        return webDriverCachePath;
    }
}