package org.ek.webcrawler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ek.webcrawler.model.CrawlJob;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobResponse {

    private Long id;
    private String startUrl;
    private Integer maxDepth;
    private CrawlJob.CrawlScope crawlScope;
    private Boolean respectRobotsTxt;
    private CrawlJob.CrawlStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer totalPagesFound;
    private Integer totalPagesCrawled;

    //Convert from entity to DTO
    public static CrawlJobResponse fromEntity(CrawlJob job) {
        return CrawlJobResponse.builder()
                .id(job.getId())
                .startUrl(job.getStartUrl())
                .maxDepth(job.getMaxDepth())
                .crawlScope(job.getCrawlScope())
                .respectRobotsTxt(job.getRespectRobotsTxt())
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .totalPagesFound(job.getTotalPagesFound())
                .totalPagesCrawled(job.getTotalPagesCrawled())
                .build();
    }



}
