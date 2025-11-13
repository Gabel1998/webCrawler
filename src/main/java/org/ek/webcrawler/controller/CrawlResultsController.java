package org.ek.webcrawler.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.dto.CrawledPageResponse;
import org.ek.webcrawler.model.CrawledPage;
import org.ek.webcrawler.service.GraphExportService;
import org.ek.webcrawler.service.PageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawl-jobs/{jobId}/results")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CrawlResultsController {

    private final PageService pageService;
    private final GraphExportService graphExportService;

    /**
     * Get all pages for a crawl job.
     * GET /api/crawl-jobs/{jobId}/results/pages
     */
    @GetMapping("/pages")
    public ResponseEntity<List<CrawledPageResponse>> getAllPages(@PathVariable Long jobId) {
        log.info("Getting all pages for job {}", jobId);

        List<CrawledPageResponse> pages = pageService.getPagesForJob(jobId)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    /**
     * Get ROOT pages (starting point
     * GET /api/crawl-jobs/{jobId}/results/root-pages
     */
    @GetMapping("/root-pages")
    public ResponseEntity<List<CrawledPageResponse>> getRootPages(@PathVariable Long jobId) {
        log.info("Getting root pages for job {}", jobId);

        List<CrawledPageResponse> pages = pageService.getRootPages(jobId)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    /**
     * Get pages for hierarchy level
     * GET /api/crawl-jobs/{jobId}/results/pages?level={level}
     */
    @GetMapping(value = "/pages", params = "level")
    public ResponseEntity<List<CrawledPageResponse>> getPagesByLevel(
            @PathVariable Long jobId,
            @RequestParam Integer level) {

        log.info("Getting pages at level {} for job: {}", level, jobId);

        List<CrawledPageResponse> pages = pageService.getPagesByLevel(jobId, level)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    @GetMapping("/graph-data")
    public ResponseEntity<GraphExportService.GraphData> getGraphData(@PathVariable Long jobId) {
        log.info("Getting graph data for job {}", jobId);
        return ResponseEntity.ok(graphExportService.generateGraphData(jobId));
    }

    @GetMapping("/tree-data")
    public ResponseEntity<GraphExportService.TreeNode> getTreeData(@PathVariable Long jobId) {
        log.info("Getting tree data for job {}", jobId);
        return ResponseEntity.ok(graphExportService.generateTreeData(jobId));
    }


    /**
     * Get statistics for a crawl job.
     * GET /api/crawl-jobs/{jobId}/results/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<CrawlStatsResponse> getStats(@PathVariable Long jobId) {
        log.info("Getting crawl job stats for job {}", jobId);

        long totalPages = pageService.countPagesForJob(jobId);
        long successfulPages = pageService.getPagesForJob(jobId)
                .stream()
                .filter(CrawledPage::getIsSuccessful)
                .count();
        long failedPages = totalPages - successfulPages;

        CrawlStatsResponse stats = CrawlStatsResponse.builder()
                .totalPages(totalPages)
                .successfulPages(successfulPages)
                .failedPages(failedPages)
                .build();


        return ResponseEntity.ok(stats);
    }

    @lombok.Data
    @lombok.Builder
    static class CrawlStatsResponse {
        private long totalPages;
        private long successfulPages;
        private long failedPages;
    }

}
