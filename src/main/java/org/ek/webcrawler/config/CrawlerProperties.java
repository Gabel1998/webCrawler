package org.ek.webcrawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.crawler")
@Data
public class CrawlerProperties {

    private Integer defaultTimeout = 10000;
    private String userAgent = "WebCrawler/1.0";
}