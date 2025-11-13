package org.ek.webcrawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.output")
@Data
public class OutputProperties {
    private String directory = "./output";

}
