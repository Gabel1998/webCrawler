package org.ek.webcrawler.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.model.CrawledPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerService {

    private final CrawlJobService crawlJobService;
    private final PageService pageService;


    /*
     *Start crawling process for a given crawl job. (Async method)
     */
    @Async
    public void startCrawling(Long jobId) {
        log.info("Starting crawler for job: {}", jobId);

        try {
            // Get the job
            CrawlJob job = crawlJobService.getJob(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

            // Mark as started
            crawlJobService.startJob(jobId);

            // Start crawling from root URL
            Set<String> visitedUrls = new HashSet<>();
            crawlPage(job, job.getStartUrl(), 0, null, visitedUrls);

            // ✅ ALWAYS mark as completed (even if partial)
            crawlJobService.completeJob(jobId);

            log.info("Crawling completed for job: {}. Total pages: {}", jobId, visitedUrls.size());

        } catch (Exception e) {
            log.error("Error during crawling job {}: {}", jobId, e.getMessage(), e);
            crawlJobService.failJob(jobId, e.getMessage());
        }
    }


    /**
     * Recursively crawl a page
     */
    private void crawlPage(
            CrawlJob job,
            String url,
            int currentDepth,
            CrawledPage parentPage,
            Set<String> visitedUrls
    ) {
        // Check if max depth reached
        if (currentDepth > job.getMaxDepth()) {
            log.debug("Max depth reached for URL: {}", url);
            return;
        }

        // Check if already visited
        if (visitedUrls.contains(url)) {
            log.debug("URL already visited: {}", url);
            return;
        }

        // ✅ ADD: Safety limit - max 100 pages per job
        if (visitedUrls.size() >= 100) {
            log.warn("Safety limit reached: 100 pages crawled for job {}", job.getId());
            return;
        }

        visitedUrls.add(url);

        log.info("Crawling URL: {} (depth: {}, total visited: {})", url, currentDepth, visitedUrls.size());

        try {
            // Fetch the page
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; WebCrawler/1.0)")
                    .timeout(10000)
                    .followRedirects(true)  // ✅ ADD: Follow redirects
                    .ignoreHttpErrors(true)  // ✅ ADD: Don't crash on 404s
                    .get();

            // Save the page
            CrawledPage page = pageService.savePage(
                    job, url, doc.title(), currentDepth, parentPage,
                    200, "text/html", true
            );

            // Update statistics
            long totalPages = pageService.countPagesForJob(job.getId());
            crawlJobService.updateJobStats(job.getId(), (int) totalPages, (int) totalPages);
            log.info("Job {} stats updated: {} pages", job.getId(), totalPages);

            // Extract and crawl links (only if not at max depth)
            if (currentDepth < job.getMaxDepth()) {
                Elements links = doc.select("a[href]");
                log.debug("Found {} links on {}", links.size(), url);

                int crawledLinks = 0;
                for (Element link : links) {
                    String nextUrl = link.absUrl("href");

                    // ✅ ADD: Limit links per page
                    if (crawledLinks >= 10) {
                        log.debug("Link limit reached for page: {}", url);
                        break;
                    }

                    // Validate URL
                    if (shouldCrawl(job, nextUrl, url)) {
                        crawlPage(job, nextUrl, currentDepth + 1, page, visitedUrls);
                        crawledLinks++;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error crawling {}: {}", url, e.getMessage());
            pageService.saveFailedPage(job, url, currentDepth, parentPage, e.getMessage());
        }
    }

    private boolean shouldCrawl(CrawlJob job, String url, String parentUrl) {
        try {
            //Parse URLs
            URI uri = new URI(url);
            URI parentUri = new URI(parentUrl);

            //Skip empty or invalid URLs
            if (url == null || url.isEmpty() || uri.getHost() == null) {
                return false;
            }

            //skip non-http(s) URLs
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return false;
            }

            //Check scope
            switch (job.getCrawlScope()) {
                case SAME_DOMAIN:
                    //Only same domain (e.g., facebook.com = facebook.com)
                    String parentDomain = parentUri.getHost();
                    String targetDomain = uri.getHost();
                    return targetDomain.equals(parentDomain);

                case INCLUDE_SUBDOMAINS:
                    //Include subdomains (e.g., m.facebook.com = facebook.com)
                    String baseDomain = getBaseDomain(parentUri.getHost());
                    String targetBaseDomain = getBaseDomain(uri.getHost());
                    return targetBaseDomain.equals(baseDomain);


                case ALL_LINKS:
                    //crawl everything
                    return true;

                default:
                    return false;
            }

        } catch (Exception e) {
            log.debug("Invalid URL: {}", url);
            return false;
        }


    }

    /**
     * Extract base domain from host (e.g., m.facebook.com -> facebook.com)
     */
    private String getBaseDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }

}
