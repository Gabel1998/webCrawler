package org.ek.webcrawler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ek.webcrawler.model.CrawledPage;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawledPageResponse {

    private Long id;
    private String url;
    private String title;
    private Integer hieraxyLevel;
    private Integer httpStatusCode;
    private String contentType;
    private Boolean isSuccessful;
    private Boolean isApi;
    private Boolean isJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private Integer outgoingLinksCount;
    private Long parentPageId;

    // ============================================
    // AI Klassificering felter
    // ============================================

    private String category;      // AI klassificeret kategori
    private Boolean aiAnalyzed;   // Om siden er blevet analyseret

    /**
     * Konverter fra entity til DTO
     */
    public static CrawledPageResponse fromEntity(CrawledPage page){
        return CrawledPageResponse.builder()
                .id(page.getId())
                .url(page.getUrl())
                .title(page.getTitle())
                .hieraxyLevel(page.getHierarchyLevel())
                .httpStatusCode(page.getHttpStatusCode())
                .contentType(page.getContentType())
                .isSuccessful(page.getIsSuccessful())
                .isApi(page.getIsApi())
                .isJson(page.getIsJson())
                .createdAt(page.getCrawledAt())
                .outgoingLinksCount(page.getOutgoingLinksCount())
                .parentPageId(page.getParentPage() != null ? page.getParentPage().getId() : null)
                .category(page.getCategory())           // NYTILFØJET
                .aiAnalyzed(page.getAiAnalyzed())       // NYTILFØJET
                .build();
    }
}