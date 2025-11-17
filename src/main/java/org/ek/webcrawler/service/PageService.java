package org.ek.webcrawler.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawlJob;
import org.ek.webcrawler.model.CrawledPage;
import org.ek.webcrawler.model.PageLink;
import org.ek.webcrawler.repository.CrawledPageRepository;
import org.ek.webcrawler.repository.PageLinkRepository;
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
     * Gem en crawlet side (UDEN tekstindhold - backwards compatible)
     */
    @Transactional
    public CrawledPage savePage(CrawlJob job, String url, String title,
                                Integer hierarchyLevel, CrawledPage parentPage,
                                Integer statusCode, String contentType,
                                Boolean isSuccessful) {
        return savePage(job, url, title, hierarchyLevel, parentPage, statusCode, contentType, isSuccessful, null);
    }

    /**
     * Gem en crawlet side MED tekstindhold (til AI klassificering)
     *
     * @param textContent Tekstindhold fra siden (første 5000 chars)
     */
    @Transactional
    public CrawledPage savePage(CrawlJob job, String url, String title,
                                Integer hierarchyLevel, CrawledPage parentPage,
                                Integer statusCode, String contentType,
                                Boolean isSuccessful, String textContent) {

        log.debug("Saving page: {} (level: {})", url, hierarchyLevel);

        CrawledPage page = CrawledPage.builder()
                .crawlJob(job)
                .url(url)
                .title(title)
                .hierarchyLevel(hierarchyLevel)
                .parentPage(parentPage)
                .httpStatusCode(statusCode)
                .contentType(contentType)
                .isSuccessful(isSuccessful)
                .crawledAt(LocalDateTime.now())
                .outgoingLinksCount(0)
                .isApi(false)
                .isJson(contentType != null && contentType.contains("json"))
                .textContent(textContent)  // TILFØJET: Gem tekstindhold til AI
                .build();

        return crawledPageRepository.save(page);
    }

    /**
     * Gem en fejlet side
     */
    @Transactional
    public CrawledPage saveFailedPage(CrawlJob job, String url, Integer hierarchyLevel,
                                      CrawledPage parentPage, String errorMessage) {

        log.warn("Saving failed page: {} - {}", url, errorMessage);

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
     * Opret et link mellem to sider
     */
    @Transactional
    public PageLink createLink(CrawledPage sourcePage, CrawledPage targetPage,
                               String linkText, PageLink.LinkType linkType) {

        log.debug("Creating link from {} to {}", sourcePage.getUrl(), targetPage.getUrl());

        PageLink link = PageLink.builder()
                .sourcePage(sourcePage)
                .targetPage(targetPage)
                .linkText(linkText)
                .linkType(linkType)
                .build();

        // Opdater outgoing links count
        sourcePage.setOutgoingLinksCount(sourcePage.getOutgoingLinksCount() + 1);
        crawledPageRepository.save(sourcePage);

        return pageLinkRepository.save(link);
    }

    /**
     * Check om URL allerede er crawlet for dette job
     */
    public boolean isUrlCrawled(Long jobId, String url) {
        return crawledPageRepository.findByCrawlJobIdAndUrl(jobId, url).isPresent();
    }

    /**
     * Hent side via URL
     */
    public Optional<CrawledPage> getPageByUrl(Long jobId, String url) {
        return crawledPageRepository.findByCrawlJobIdAndUrl(jobId, url);
    }

    /**
     * Hent alle sider for et job
     */
    public List<CrawledPage> getPagesForJob(Long jobId) {
        return crawledPageRepository.findByCrawlJobId(jobId);
    }

    /**
     * Hent root sider for et job
     */
    public List<CrawledPage> getRootPages(Long jobId) {
        return crawledPageRepository.findRootPagesByJobId(jobId);
    }

    /**
     * Hent sider på specifikt hierarki niveau
     */
    public List<CrawledPage> getPagesByLevel(Long jobId, Integer level) {
        return crawledPageRepository.findByCrawlJobIdAndHierarchyLevel(jobId, level);
    }

    /**
     * Hent alle links fra en side
     */
    public List<PageLink> getLinksFromPage(Long pageId) {
        return pageLinkRepository.findBySourcePageId(pageId);
    }

    /**
     * Tæl total sider for et job
     */
    public long countPagesForJob(Long jobId) {
        return crawledPageRepository.countByCrawlJobId(jobId);
    }
}