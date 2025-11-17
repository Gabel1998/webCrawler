package org.ek.webcrawler.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.dto.CrawlJobResponse;
import org.ek.webcrawler.dto.CreateCrawlJobRequest;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.service.CrawlJobService;
import org.ek.webcrawler.service.CrawlerService;
import org.ek.webcrawler.service.PageClassificationService;  // NYTILFØJET
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawl-jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CrawlJobController {

    private final CrawlJobService crawlJobService;
    private final CrawlerService crawlerService;
    private final PageClassificationService classificationService;  // NYTILFØJET

    /**
     * Opret nyt crawl job
     * POST /api/crawl-jobs
     */
    @PostMapping
    public ResponseEntity<CrawlJobResponse> createCrawlJob(@RequestBody CreateCrawlJobRequest request) {
        log.info("Modtaget request til at oprette crawl job for URL: {}", request.getStartUrl());

        request.validate();

        CrawlJob job = crawlJobService.createCrawlJob(
                request.getStartUrl(),
                request.getMaxDepth(),
                request.getCrawlScope(),
                request.getRespectRobotsTxt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CrawlJobResponse.fromEntity(job));
    }

    /**
     * Start crawl job
     * POST /api/crawl-jobs/{id}/start
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<CrawlJobResponse> startCrawlJob(@PathVariable Long id) {
        log.info("Starter crawl job: {}", id);

        CrawlJob job = crawlJobService.getJob(id)
                .orElseThrow(() -> new RuntimeException("Crawl job ikke fundet for ID: " + id));

        if (job.getStatus() != CrawlJob.CrawlStatus.PENDING) {
            throw new IllegalStateException("Job kan ikke startes. Nuværende status: " + job.getStatus());
        }

        crawlerService.startCrawling(id);

        return ResponseEntity.ok(CrawlJobResponse.fromEntity(job));
    }

    /**
     * Hent specifikt crawl job
     * GET /api/crawl-jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CrawlJobResponse> getCrawlJob(@PathVariable Long id) {
        log.info("Henter crawl job: {}", id);

        CrawlJob job = crawlJobService.getJob(id)
                .orElseThrow(() -> new RuntimeException("Crawl job ikke fundet for ID: " + id));

        return ResponseEntity.ok(CrawlJobResponse.fromEntity(job));
    }

    /**
     * Hent alle crawl jobs
     * GET /api/crawl-jobs
     */
    @GetMapping
    public ResponseEntity<List<CrawlJobResponse>> getAllCrawlJobs() {
        log.info("Henter alle crawl jobs");

        List<CrawlJobResponse> jobs = crawlJobService.getAllJobs()
                .stream()
                .map(CrawlJobResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(jobs);
    }

    /**
     * Hent crawl jobs efter status
     * GET /api/crawl-jobs?status={COMPLETED}
     */
    @GetMapping(params = "status")
    public ResponseEntity<List<CrawlJobResponse>> getCrawlJobsByStatus(@RequestParam CrawlJob.CrawlStatus status) {
        log.info("Henter crawl jobs med status: {}", status);

        List<CrawlJobResponse> jobs = crawlJobService.getJobsByStatus(status)
                .stream()
                .map(CrawlJobResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(jobs);
    }

    /**
     * Slet crawl job
     * DELETE /api/crawl-jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrawlJob(@PathVariable Long id) {
        log.info("Sletter crawl job: {}", id);
        return ResponseEntity.ok().build();
    }

    // ============================================
    // NYE AI KLASSIFICERING ENDPOINTS
    // ============================================

    /**
     * Klassificer alle sider i et crawl job med AI
     * POST /api/crawl-jobs/{id}/classify
     *
     * Starter asynkron klassificering af alle sider i jobbet.
     * Returnerer med det samme - klassificering kører i baggrunden.
     *
     * Brug /classification-stats endpoint til at følge fremskridt.
     */
    @PostMapping("/{id}/classify")
    public ResponseEntity<Map<String, String>> classifyCrawlJob(@PathVariable Long id) {
        log.info("Starter AI klassificering for crawl job: {}", id);

        // Verificer at job eksisterer og er færdigt
        CrawlJob job = crawlJobService.getJob(id)
                .orElseThrow(() -> new RuntimeException("Crawl job ikke fundet for ID: " + id));

        if (job.getStatus() != CrawlJob.CrawlStatus.COMPLETED) {
            throw new IllegalStateException("Job skal være færdigt før klassificering. Nuværende status: " + job.getStatus());
        }

        // Start klassificering asynkront (returnerer med det samme)
        classificationService.classifyAllPages(id);

        Map<String, String> response = new HashMap<>();
        response.put("status", "Classification started");
        response.put("jobId", id.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Hent klassificerings statistik for et crawl job
     * GET /api/crawl-jobs/{id}/classification-stats
     *
     * Returnerer:
     * - Antal sider i alt
     * - Antal analyserede sider
     * - Antal fejl
     * - Fordeling på kategorier (categoryBreakdown)
     *
     * Brug denne til at følge fremskridt under klassificering.
     */
    @GetMapping("/{id}/classification-stats")
    public ResponseEntity<PageClassificationService.ClassificationStats> getClassificationStats(@PathVariable Long id) {
        log.info("Henter klassificerings statistik for job: {}", id);

        PageClassificationService.ClassificationStats stats = classificationService.getStats(id);

        return ResponseEntity.ok(stats);
    }
}