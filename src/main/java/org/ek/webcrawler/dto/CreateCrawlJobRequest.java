package org.ek.webcrawler.dto;

import lombok.Data;
import org.ek.webcrawler.model.CrawlJob;

@Data
public class CreateCrawlJobRequest {

    private String startUrl;
    private Integer maxDepth;
    private CrawlJob.CrawlScope crawlScope;
    private Boolean respectRobotsTxt;

    //Validation
    public void validate() {
        if (startUrl == null || startUrl.isEmpty()) {
            throw new IllegalArgumentException("Start URL cannot be null or empty");
        }
        if (maxDepth == null || maxDepth < 0) {
            throw new IllegalArgumentException("Max depth must be a non-negative integer");
        }
        if (crawlScope == null) {
            throw new IllegalArgumentException("Crawl scope cannot be null");
        }
        if (respectRobotsTxt == null) {
            throw new IllegalArgumentException("Respect robots.txt flag cannot be null");
        }
    }
}
