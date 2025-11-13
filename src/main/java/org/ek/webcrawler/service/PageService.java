package org.ek.webcrawler.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.model.CrawledPage;
import org.ek.webcrawler.model.PageLink;
import org.ek.webcrawler.repository.CrawlJobRepository;
import org.ek.webcrawler.repository.CrawledPageRepository;
import org.ek.webcrawler.repository.PageLinkRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageService {

    private final CrawledPageRepository crawledPageRepository;
    private final PageLinkRepository pageLinkRepository;

    /**
     * Save a crawled page.
     */
    @Transactional
    public CrawledPage savePage(CrawlJob job, String url, String title,
                                Integer hierarchyLevel, CrawledPage parentPage,
                                Integer statusCode, String contentType,
                                Boolean isSuccesfull) {

        log.debug("Saving page: {} (level: {}) ", url, hierarchyLevel);
        CrawledPage page = CrawledPage.builder()
                .crawlJob(job)
                .url(url)
                .title(title)
                .hierarchyLevel(hierarchyLevel)
                .parentPage(parentPage)
                .httpStatusCode(statusCode)
                .contentType(contentType)
                .isSuccessful(isSuccesfull)
                .crawledAt(LocalDateTime.now())
                .outgoingLinksCount(0)
                .isApi(false)
                .isJson(contentType != null && contentType.contains("json"))
                .build();

        return crawledPageRepository.save(page);
    }

    /**
     * Save a failed page
     */
    @Transactional
    public CrawledPage saveFailedPage(CrawlJob job, String url, Integer hierarchyLevel,
                                      CrawledPage parentPage, String errorMessage) {

        log.warn("Saving failed page: {} - {} ", url, errorMessage);

        CrawledPage page = CrawledPage.builder()
                .crawlJob(job)
                .url(url)
                .hierarchyLevel(hierarchyLevel)
                .parentPage(parentPage)
                .isSuccessful(false)
                .errorMessage(errorMessage)
                .crawledAt(LocalDateTime.now())
                .build();

        return crawledPageRepository.save(page);

    }

    /**
     * Create a link between two pages.
     */
    @Transactional
    public PageLink createLink(CrawledPage sourcePage, CrawledPage targetPage,
                           String linkText, PageLink.LinkType linkType) {

        log.debug("Creating link from {} to {} ", sourcePage.getUrl(), targetPage.getUrl());

        PageLink link = PageLink.builder()
                .sourcePage(sourcePage)
                .targetPage(targetPage)
                .linkText(linkText)
                .linkType(linkType)
                .build();

        //update outgoing links count
        sourcePage.setOutgoingLinksCount(sourcePage.getOutgoingLinksCount() + 1);
        crawledPageRepository.save(sourcePage);

        return pageLinkRepository.save(link);

    }

    /**
     * Check if URL has already been crawled for this job
     */
    public boolean isUrlCrawled(Long jobId, String url) {
        return crawledPageRepository.findByCrawlJobIdAndUrl(jobId, url).isPresent();
    }

    /**
     * Get page by URL
     */
    public Optional<CrawledPage> getPageByUrl(Long jobId, String url) {
        return crawledPageRepository.findByCrawlJobIdAndUrl(jobId, url);
    }

    /**
     * get all pages for a job
     */
    public List<CrawledPage> getPagesForJob(Long jobId) {
        return crawledPageRepository.findByCrawlJobId(jobId);
    }

    /**
     * Get root page for a job
     */
    public List<CrawledPage> getRootPages(Long jobId) {
        return crawledPageRepository.findRootPagesByJobId(jobId);
    }

    /**
     * Get pages at specific hierarchy level
     */
    public List<CrawledPage> getPagesByLevel(Long jobId, Integer level) {
        return crawledPageRepository.findByCrawlJobIdAndHierarchyLevel(jobId, level);
    }

    /**
     * Get all links for a page
     */
    public List<PageLink> getLinksFromPage(Long pageId) {
        return pageLinkRepository.findBySourcePageId(pageId);
    }

    /**
     * Count total pages for a job
     */
    public long countPagesForJob(Long jobId) {
        return crawledPageRepository.countByCrawlJobId(jobId);
    }

}


