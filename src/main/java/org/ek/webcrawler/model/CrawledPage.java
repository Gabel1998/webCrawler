package org.ek.webcrawler.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crawled_pages", indexes = {
        @Index(name = "idx_url", columnList = "url"),
        @Index(name = "idx_job_level", columnList = "crawl_job_id, hierarchy_level")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawledPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crawl_job_id", nullable = false)
    private CrawlJob crawlJob;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(nullable = false)
    @Builder.Default
    private Integer hierarchyLevel = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_page_id")
    private CrawledPage parentPage;

    @OneToMany(mappedBy = "parentPage", cascade = CascadeType.ALL)
    @Builder.Default
    private List<CrawledPage> childPages = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime crawledAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private Boolean isSuccessful = true;

    private Integer httpStatusCode;

    @Column(length = 100)
    private String contentType;

    @Builder.Default
    private Boolean isApi = false;

    @Builder.Default
    private Boolean isJson = false;

    @Column(length = 1000)
    private String errorMessage;

    @Builder.Default
    private Integer outgoingLinksCount = 0;

    @OneToMany(mappedBy = "sourcePage", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PageLink> outgoingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "targetPage")
    @Builder.Default
    private List<PageLink> incomingLinks = new ArrayList<>();

    // ============================================
    // AI Klassificering felter
    // ============================================

    /**
     * AI klassificeret kategori: PRODUCT, CATEGORY, INFORMATION, BLOG,
     * CONTACT, JOB, LEGAL, HOME, UNKNOWN
     */
    @Column(length = 50)
    private String category;

    /**
     * Om siden er blevet analyseret af AI
     */
    @Builder.Default
    private Boolean aiAnalyzed = false;

    /**
     * Fejlbesked hvis AI klassificering fejler
     */
    @Column(length = 500)
    private String aiAnalysisError;

    /**
     * Tekstindhold fra siden (til AI klassificering)
     * Første 5000 karakterer af sidens tekst
     */
    @Column(columnDefinition = "TEXT")
    private String textContent;
}