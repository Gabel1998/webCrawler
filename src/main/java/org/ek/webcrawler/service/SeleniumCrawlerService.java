package org.ek.webcrawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.model.CrawledPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Selenium-based crawler for JavaScript-heavy sites
 * Uses Selenium Manager (Selenium 4.6+) - no manual ChromeDriver installation needed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeleniumCrawlerService {

    private final CrawlJobService crawlJobService;
    private final PageService pageService;
    private WebDriver driver;

    @Value("${selenium.remote.url:}")
    private String seleniumRemoteUrl;

    @Async
    public void startCrawling(Long jobId) {
        log.info("Starting Selenium crawler for job: {}", jobId);

        try {
            CrawlJob job = crawlJobService.getJob(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

            crawlJobService.startJob(jobId);

            initializeDriver();

            Set<String> visitedUrls = new HashSet<>();
            crawlPage(job, job.getStartUrl(), 0, null, visitedUrls);

            crawlJobService.completeJob(jobId);

            log.info("Crawling completed for job: {}. Total pages: {}", jobId, visitedUrls.size());

        } catch (Exception e) {
            log.error("Error during crawling job {}: {}", jobId, e.getMessage(), e);
            crawlJobService.failJob(jobId, e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                log.info("WebDriver cleaned up");
            }
        }
    }

    private void initializeDriver() throws Exception {
        log.info("Initializing Chrome WebDriver with Selenium Manager...");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

        // Add language preference for Danish
        options.addArguments("--lang=da-DK");

        // Disable notifications and popups
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        if (seleniumRemoteUrl != null && !seleniumRemoteUrl.isEmpty()) {
            log.info("Connecting to remote Selenium at: {}", seleniumRemoteUrl);
            driver = new RemoteWebDriver(new URL(seleniumRemoteUrl), options);
        } else {
            driver = new ChromeDriver(options);
        }
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

        log.info("WebDriver initialized successfully");
    }

    /**
     * Attempt to close cookie banners and popups
     */
    private void handleCookieConsent() {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

            // Common cookie banner selectors
            String[] cookieSelectors = {
                    "button[id*='accept']",
                    "button[class*='accept']",
                    "button[id*='cookie']",
                    "button[class*='cookie']",
                    "a[class*='accept']",
                    "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
                    ".cookie-accept",
                    ".accept-cookies",
                    "[data-testid='cookie-accept']"
            };

            for (String selector : cookieSelectors) {
                try {
                    var button = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                    button.click();
                    log.info("Clicked cookie consent button: {}", selector);
                    Thread.sleep(500);
                    return;
                } catch (Exception e) {
                    // Try next selector
                }
            }

            log.debug("No cookie banner found or already accepted");

        } catch (Exception e) {
            log.debug("Error handling cookie consent: {}", e.getMessage());
        }
    }

    /**
     * Scroll page to trigger lazy loading
     */
    private void scrollPage() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Scroll to bottom
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            Thread.sleep(1000);

            // Scroll to middle
            js.executeScript("window.scrollTo(0, document.body.scrollHeight / 2)");
            Thread.sleep(500);

            // Scroll to top
            js.executeScript("window.scrollTo(0, 0)");
            Thread.sleep(500);

            log.debug("Page scrolling completed");

        } catch (Exception e) {
            log.debug("Error during scrolling: {}", e.getMessage());
        }
    }

    /**
     * Extract clean text content from page for AI classification
     * Removes scripts, styles, and limits to 5000 characters
     */
    private String extractTextContent(Document doc) {
        try {
            // Remove unwanted elements
            doc.select("script, style, noscript, iframe, svg").remove();

            // Get text from body
            String text = doc.body().text();

            // Clean up whitespace
            text = text.replaceAll("\\s+", " ").trim();

            // Limit to 5000 characters for AI processing
            if (text.length() > 5000) {
                text = text.substring(0, 5000);
            }

            log.debug("Extracted {} characters of text content", text.length());
            return text;

        } catch (Exception e) {
            log.warn("Error extracting text content: {}", e.getMessage());
            return "";
        }
    }

    private void crawlPage(
            CrawlJob job,
            String url,
            int currentDepth,
            CrawledPage parentPage,
            Set<String> visitedUrls
    ) {
        if (currentDepth > job.getMaxDepth()) {
            log.debug("Max depth reached for URL: {}", url);
            return;
        }

        String normalizedUrl = normalizeUrl(url);
        if (visitedUrls.contains(normalizedUrl)) {
            log.debug("URL already visited (normalized): {}", normalizedUrl);
            return;
        }

        if (visitedUrls.size() >= 100) {
            log.warn("Safety limit reached: 100 pages crawled for job {}", job.getId());
            return;
        }

        visitedUrls.add(normalizedUrl);
        log.info("Crawling URL: {} (depth: {}, total: {})", url, currentDepth, visitedUrls.size());

        try {
            driver.get(url);

            // Handle cookie consent on first page only
            if (currentDepth == 0) {
                Thread.sleep(1000);
                handleCookieConsent();
            }

            // Wait for page to load
            Thread.sleep(2000);

            // Scroll to trigger lazy loading
            scrollPage();

            // Extra wait for dynamic content
            Thread.sleep(1000);

            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource, url);

            //  Extract text content for AI classification
            String textContent = extractTextContent(doc);

            //  Save page WITH text content
            CrawledPage page = pageService.savePage(
                    job, url, doc.title(), currentDepth, parentPage,
                    200, "text/html", true, textContent
            );

            long totalPages = pageService.countPagesForJob(job.getId());
            crawlJobService.updateJobStats(job.getId(), (int) totalPages, (int) totalPages);

            if (currentDepth < job.getMaxDepth()) {
                Elements links = doc.select("a[href]");
                log.info("Found {} total <a> tags on page: {}", links.size(), url);

                // Filter out empty or invalid hrefs
                List<Element> validLinks = new ArrayList<>();
                for (Element link : links) {
                    String href = link.attr("abs:href");
                    if (href != null && !href.isEmpty() && (href.startsWith("http://") || href.startsWith("https://"))) {
                        validLinks.add(link);
                    }
                }
                log.info("Filtered to {} valid HTTP(S) links", validLinks.size());

                List<Element> prioritizedLinks = new ArrayList<>();
                List<Element> otherLinks = new ArrayList<>();

                for (Element link : validLinks) {
                    String text = link.text().toLowerCase();
                    String className = link.attr("class").toLowerCase();
                    String id = link.attr("id").toLowerCase();

                    // Check if link is in navigation area or has navigation-related attributes
                    boolean isNavigation = link.parents().select("nav, header, footer, [class*=menu], [class*=navigation], [role=navigation]").size() > 0
                            || className.contains("nav") || className.contains("menu")
                            || id.contains("nav") || id.contains("menu")
                            || text.matches(".*(menu|navigation|kategori|produkter|lÃ¸sninger|brand|ekspert).*");

                    if (isNavigation) {
                        prioritizedLinks.add(link);
                    } else {
                        otherLinks.add(link);
                    }
                }

                List<Element> orderedLinks = new ArrayList<>(prioritizedLinks);
                orderedLinks.addAll(otherLinks);

                log.info("Prioritized {} navigation links out of {} valid links", prioritizedLinks.size(), validLinks.size());

                int crawledLinks = 0;
                for (Element link : orderedLinks) {
                    String nextUrl = link.absUrl("href");

                    if (crawledLinks >= 50) {
                        log.debug("Link limit (50) reached for page: {}", url);
                        break;
                    }

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
            URI uri = new URI(url);
            URI parentUri = new URI(parentUrl);

            if (url == null || url.isEmpty() || uri.getHost() == null) {
                return false;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return false;
            }

            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("/cart") || lowerUrl.contains("/checkout")
                    || lowerUrl.contains("/login") || lowerUrl.contains("/account")
                    || lowerUrl.contains(".pdf") || lowerUrl.contains(".jpg")
                    || lowerUrl.contains(".png") || lowerUrl.contains(".gif")
                    || lowerUrl.contains(".zip") || lowerUrl.contains(".mp4")) {
                return false;
            }

            switch (job.getCrawlScope()) {
                case SAME_DOMAIN:
                    String parentHost = parentUri.getHost().replaceAll("^www\\.", "");
                    String targetHost = uri.getHost().replaceAll("^www\\.", "");
                    return targetHost.equals(parentHost);

                case INCLUDE_SUBDOMAINS:
                    String baseDomain = getBaseDomain(parentUri.getHost());
                    String targetBaseDomain = getBaseDomain(uri.getHost());
                    return targetBaseDomain.equals(baseDomain);

                case ALL_LINKS:
                    return true;

                default:
                    return false;
            }

        } catch (Exception e) {
            log.debug("Invalid URL: {}", url);
            return false;
        }
    }

    private String getBaseDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }

    private String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);

            String protocol = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();

            String path = uri.getPath();
            if (path != null && !path.equals("/")) {
                path = path.replaceAll("/+$", "");
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                String[] params = query.split("&");
                Arrays.sort(params);
                query = String.join("&", params);
            }

            StringBuilder normalized = new StringBuilder();
            normalized.append(protocol).append("://").append(host);

            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {
                normalized.append(":").append(port);
            }

            normalized.append(path);

            if (query != null && !query.isEmpty()) {
                normalized.append("?").append(query);
            }

            return normalized.toString();

        } catch (Exception e) {
            log.warn("Failed to normalize URL: {}, using original", url);
            return url;
        }
    }
}