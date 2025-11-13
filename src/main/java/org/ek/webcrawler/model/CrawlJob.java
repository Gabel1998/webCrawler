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
@Table(name = "crawl_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String startUrl;

    @Column(nullable = false)
    private Integer maxDepth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrawlScope crawlScope;

    @Column(nullable = false)
    private Boolean respectRobotsTxt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CrawlStatus status = CrawlStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String errorMessage;

    @Builder.Default
    private Integer totalPagesFound = 0;

    @Builder.Default
    private Integer totalPagesCrawled = 0;

    // FIX: Changed from List<CrawlJob> to List<CrawledPage>
    // FIX: Changed mappedBy from "CrawlJob" to "crawlJob" (lowercase)
    @OneToMany(mappedBy = "crawlJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CrawledPage> pages = new ArrayList<>();

    @Column(length = 500)
    private String xmlOutputPath;

    @Column(length = 500)
    private String graphOutputPath;

    // ENUMS
    public enum CrawlStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum CrawlScope {
        SAME_DOMAIN,        // Only same domain (e.g., facebook.com)
        INCLUDE_SUBDOMAINS, // Include subdomains (e.g., m.facebook.com)
        ALL_LINKS           // Follow all links regardless of domain
    }
}