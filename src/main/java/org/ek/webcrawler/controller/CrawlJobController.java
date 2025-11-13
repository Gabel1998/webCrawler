package org.ek.webcrawler.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.dto.CrawlJobResponse;
import org.ek.webcrawler.dto.CreateCrawlJobRequest;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.service.CrawlJobService;
import org.ek.webcrawler.service.CrawlerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawl-jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CrawlJobController {

    private final CrawlJobService crawlJobService;
    private final CrawlerService crawlerService;

    /**
     * Create a new crawl job.
     * POST /api/crawl-jobs
     */
    @PostMapping
    public ResponseEntity<CrawlJobResponse> createCrawlJob(@RequestBody CreateCrawlJobRequest request) {
        log.info("Received request to create crawl job for URL: {}", request.getStartUrl());

        //Validate request
        request.validate();

        //Create Job
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
     * Start crawl job.
     * POST /api/crawl-jobs/{id}/start
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<CrawlJobResponse> startCrawlJob(@PathVariable Long id) {

        log.info("Starting a crawl job: {}", id);

        //Get job
        CrawlJob job = crawlJobService.getJob(id)
                .orElseThrow(() -> new RuntimeException("Crawl job not found for ID: " + id));

        //Check if already in progress or completed
        if (job.getStatus() != CrawlJob.CrawlStatus.PENDING) {
            throw new IllegalStateException("Job Cannot be started. Current status: " + job.getStatus());
        }

        //Start crawling asynchronously
        crawlerService.startCrawling(id);

        //Return immediatly (Job is now IN_PROGRESS)
        return ResponseEntity.ok(CrawlJobResponse.fromEntity(job));
    }

    /**
     * Get a specific crawl job by ID.
     * GET /api/crawl-jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CrawlJobResponse> getCrawlJob(@PathVariable Long id) {
        log.info("Fetching crawl job: {}", id);

        CrawlJob job = crawlJobService.getJob(id)
                .orElseThrow(() -> new RuntimeException("Crawl job not found for ID: " + id));

        return ResponseEntity.ok(CrawlJobResponse.fromEntity(job));
    }

    /**
     * Get all crawl jobs.
     * GET /api/crawl-jobs
     */
    @GetMapping
    public ResponseEntity<List<CrawlJobResponse>> getAllCrawlJobs() {
        log.info("Fetching all crawl jobs");

        List<CrawlJobResponse> jobs = crawlJobService.getAllJobs()
                .stream()
                .map(CrawlJobResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(jobs);
    }

    /**
     * Get crawl jobs by status.
     * GET /api/crawl-jobs?status={COMPLETED}
     */
    @GetMapping(params = "status")
    public ResponseEntity<List<CrawlJobResponse>> getCrawlJobsByStatus(@RequestParam CrawlJob.CrawlStatus status) {
        log.info("Fetching crawl jobs with status: {}", status);

        List<CrawlJobResponse> jobs = crawlJobService.getJobsByStatus(status)
                .stream()
                .map(CrawlJobResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(jobs);
    }

    /**
     * Delete a crawl job by ID.
     * DELETE /api/crawl-jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrawlJob(@PathVariable Long id) {
        log.info("Deleting crawl job: {}", id);

        //TODO: Implement delete in service
        //For now just return 204 no content

        return ResponseEntity.ok().build();
    }


}
