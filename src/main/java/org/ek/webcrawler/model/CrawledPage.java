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
    private Boolean isApi = false;  // Changed from boolean to Boolean

    @Builder.Default
    private Boolean isJson = false;  // Changed from boolean to Boolean

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
}