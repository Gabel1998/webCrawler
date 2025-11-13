package org.ek.webcrawler.repository;

import org.ek.webcrawler.model.PageLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageLinkRepository extends JpaRepository<PageLink, Long> {

    // Find all links from a specific page (using page ID)
    List<PageLink> findBySourcePageId(Long sourcePageId);

    // Find all links to a specific page
    List<PageLink> findByTargetPageId(Long targetPageId);

    // Find all links of a specific type from a page
    List<PageLink> findBySourcePageIdAndLinkType(Long sourcePageId, PageLink.LinkType linkType);

    // Count outgoing links from a page
    long countBySourcePageId(Long sourcePageId);

    // Count incoming links to a page
    long countByTargetPageId(Long targetPageId);

    // Find all API links in a crawl job
    @Query("SELECT pl FROM PageLink pl WHERE pl.sourcePage.crawlJob.id = :jobId AND pl.linkType = 'API'")
    List<PageLink> findApiLinksByJobId(@Param("jobId") Long jobId);
}