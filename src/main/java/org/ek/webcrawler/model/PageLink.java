package org.ek.webcrawler.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "page_links", indexes = {
        @Index(name = "idx_source_page", columnList = "source_page_id"),
        @Index(name = "idx_target_page", columnList = "target_page_id")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_page_id", nullable = false)
    private CrawledPage sourcePage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_page_id", nullable = true)  // Changed to nullable
    private CrawledPage targetPage;

    @Column(length = 100)
    private String linkText;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LinkType linkType = LinkType.INTERNAL;

    public enum LinkType {
        INTERNAL,   // Link within the crawl scope
        EXTERNAL,   // Link outside the crawl scope
        API,        // API endpoint
        RESOURCE    // CSS, JS, image, etc.
    }

}
