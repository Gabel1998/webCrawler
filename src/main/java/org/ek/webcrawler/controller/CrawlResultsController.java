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
     * Hent alle sider for et crawl job
     * GET /api/crawl-jobs/{jobId}/results/pages
     */
    @GetMapping("/pages")
    public ResponseEntity<List<CrawledPageResponse>> getAllPages(@PathVariable Long jobId) {
        log.info("Henter alle sider for job {}", jobId);

        List<CrawledPageResponse> pages = pageService.getPagesForJob(jobId)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    /**
     * Hent root sider (startpunkter)
     * GET /api/crawl-jobs/{jobId}/results/root-pages
     */
    @GetMapping("/root-pages")
    public ResponseEntity<List<CrawledPageResponse>> getRootPages(@PathVariable Long jobId) {
        log.info("Henter root sider for job {}", jobId);

        List<CrawledPageResponse> pages = pageService.getRootPages(jobId)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    /**
     * Hent sider på specifikt hierarki niveau
     * GET /api/crawl-jobs/{jobId}/results/pages?level={level}
     */
    @GetMapping(value = "/pages", params = "level")
    public ResponseEntity<List<CrawledPageResponse>> getPagesByLevel(
            @PathVariable Long jobId,
            @RequestParam Integer level) {

        log.info("Henter sider på niveau {} for job: {}", level, jobId);

        List<CrawledPageResponse> pages = pageService.getPagesByLevel(jobId, level)
                .stream()
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    // ============================================
    // NYT ENDPOINT: Filtrer efter kategori
    // ============================================

    /**
     * Hent sider filtreret efter AI kategori
     * GET /api/crawl-jobs/{jobId}/results/pages?category={category}
     *
     * Eksempler:
     * - GET /api/crawl-jobs/1/results/pages?category=PRODUCT   -> Kun produktsider
     * - GET /api/crawl-jobs/1/results/pages?category=BLOG      -> Kun blog sider
     *
     * Mulige kategorier:
     * PRODUCT, CATEGORY, INFORMATION, BLOG, CONTACT, JOB, LEGAL, HOME, UNKNOWN
     */
    @GetMapping(value = "/pages", params = "category")
    public ResponseEntity<List<CrawledPageResponse>> getPagesByCategory(
            @PathVariable Long jobId,
            @RequestParam String category) {

        log.info("Henter sider med kategori {} for job: {}", category, jobId);

        List<CrawledPageResponse> pages = pageService.getPagesForJob(jobId)
                .stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .map(CrawledPageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pages);
    }

    /**
     * Hent graph data til visualisering
     * GET /api/crawl-jobs/{jobId}/results/graph-data
     */
    @GetMapping("/graph-data")
    public ResponseEntity<GraphExportService.GraphData> getGraphData(@PathVariable Long jobId) {
        log.info("Henter graph data for job {}", jobId);
        return ResponseEntity.ok(graphExportService.generateGraphData(jobId));
    }

    /**
     * Hent tree data til hierarkisk visualisering
     * GET /api/crawl-jobs/{jobId}/results/tree-data
     */
    @GetMapping("/tree-data")
    public ResponseEntity<GraphExportService.TreeNode> getTreeData(@PathVariable Long jobId) {
        log.info("Henter tree data for job {}", jobId);
        return ResponseEntity.ok(graphExportService.generateTreeData(jobId));
    }

    /**
     * Eksporter som GraphML XML
     * GET /api/crawl-jobs/{jobId}/results/export/graphml
     */
    @GetMapping(value = "/export/graphml", produces = "application/xml")
    public ResponseEntity<String> exportGraphML(@PathVariable Long jobId) {
        log.info("Eksporterer GraphML XML for job {}", jobId);
        String xml = graphExportService.generateGraphMLXml(jobId);

        if (xml == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=crawl-job-" + jobId + "-graphml.xml")
                .body(xml);
    }

    /**
     * Eksporter som hierarkisk XML
     * GET /api/crawl-jobs/{jobId}/results/export/xml
     */
    @GetMapping(value = "/export/xml", produces = "application/xml")
    public ResponseEntity<String> exportHierarchicalXml(@PathVariable Long jobId) {
        log.info("Eksporterer hierarkisk XML for job {}", jobId);
        String xml = graphExportService.generateHierarchicalXml(jobId);

        if (xml == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=crawl-job-" + jobId + ".xml")
                .body(xml);
    }

    /**
     * Eksporter som XML Sitemap
     * GET /api/crawl-jobs/{jobId}/results/export/sitemap
     */
    @GetMapping(value = "/export/sitemap", produces = "application/xml")
    public ResponseEntity<String> exportSitemap(@PathVariable Long jobId) {
        log.info("Eksporterer sitemap XML for job {}", jobId);
        String xml = graphExportService.generateSitemapXml(jobId);

        if (xml == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=sitemap-" + jobId + ".xml")
                .body(xml);
    }

    /**
     * Hent statistik for crawl job
     * GET /api/crawl-jobs/{jobId}/results/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<CrawlStatsResponse> getStats(@PathVariable Long jobId) {
        log.info("Henter crawl job statistik for job {}", jobId);

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