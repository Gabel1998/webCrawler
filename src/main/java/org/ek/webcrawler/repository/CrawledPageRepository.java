package org.ek.webcrawler.repository;

import org.ek.webcrawler.model.CrawledPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawledPageRepository extends JpaRepository<CrawledPage, Long> {

    // Find all pages for a specific crawl job (using job ID)
    List<CrawledPage> findByCrawlJobId(Long jobId);

    // Find all pages at a specific hierarchy level for a job
    List<CrawledPage> findByCrawlJobIdAndHierarchyLevel(Long jobId, Integer level);

    // Find a page by URL within a specific job
    Optional<CrawledPage> findByCrawlJobIdAndUrl(Long jobId, String url);

    // Find all successful pages for a job
    List<CrawledPage> findByCrawlJobIdAndIsSuccessfulTrue(Long jobId);

    // Find all API pages
    List<CrawledPage> findByCrawlJobIdAndIsApiTrue(Long jobId);

    // Count total pages for a job
    long countByCrawlJobId(Long jobId);

    // Find root pages (no parent) - needs @Query because of IS NULL check
    @Query("SELECT p FROM CrawledPage p WHERE p.crawlJob.id = :jobId AND p.parentPage IS NULL")
    List<CrawledPage> findRootPagesByJobId(@Param("jobId") Long jobId);
}