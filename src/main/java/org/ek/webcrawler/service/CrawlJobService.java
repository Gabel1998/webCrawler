package org.ek.webcrawler.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.repository.CrawlJobRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j // Logger for the class
public class CrawlJobService {

    private final CrawlJobRepository crawlJobRepository;

    /**
     * Create a new crawl job.
     */
    @Transactional
    public CrawlJob createCrawlJob(String startUrl, Integer maxDepth, CrawlJob.CrawlScope scope, Boolean respectRobotsTxt) {

        log.info("Creating new crawl job for URL: {}", startUrl);

        CrawlJob job = CrawlJob.builder()
                .startUrl(startUrl)
                .maxDepth(maxDepth)
                .crawlScope(scope)
                .respectRobotsTxt(respectRobotsTxt)
                .status(CrawlJob.CrawlStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .totalPagesFound(0)
                .totalPagesCrawled(0)
                .build();
        CrawlJob savedJob = crawlJobRepository.save(job);
        log.info("Created new crawl job with ID: {}", savedJob.getId());
        return savedJob;
    }

    /**
     * Start a crawl job (update status).
     */
    @Transactional
    public CrawlJob startJob(Long jobId) {
        log.info("Starting crawl job for ID: {}", jobId);

        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Crawljob not found for ID: " + jobId));
        job.setStatus(CrawlJob.CrawlStatus.IN_PROGRESS);
        job.setStartedAt(LocalDateTime.now());

        return crawlJobRepository.save(job);
    }

    /**
     * Complete a crawl job
     */
    @Transactional
    public CrawlJob completeJob(Long jobId) {
        log.info("Completing crawl job for ID: {}", jobId);

        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Crawljob not found for ID: " + jobId));
        job.setStatus(CrawlJob.CrawlStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());

        return crawlJobRepository.save(job);
    }


    /**
     * mark a crawl job as failed
     */
    @Transactional
    public CrawlJob failJob(Long jobId, String errorMessage) {
        log.info("Failing crawl job as failed for ID: {} Message: {}", jobId, errorMessage);

        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("CrawlJob not found for ID: " + jobId));
        job.setStatus(CrawlJob.CrawlStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(errorMessage);

        return crawlJobRepository.save(job);
    }


    /**
     * Update crawl statistics
     */
    @Transactional
    public void updateJobStats(Long jobId, int pagesFound, int pagesCrawled) {
        CrawlJob job = crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("CrawlJob not found for ID: " + jobId));

        job.setTotalPagesFound(pagesFound);
        job.setTotalPagesCrawled(pagesCrawled);

        crawlJobRepository.save(job);
    }

    /**
     * Get a job by ID
     */
    public Optional<CrawlJob> getJob(Long jobId) {
        return crawlJobRepository.findById(jobId);
    }

    /**
     * Get all crawl jobs
     */
    public List<CrawlJob> getAllJobs() {
        return crawlJobRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get a jobs by status
     */
    public List<CrawlJob> getJobsByStatus(CrawlJob.CrawlStatus status) {
        return crawlJobRepository.findByStatus(status);
    }
}