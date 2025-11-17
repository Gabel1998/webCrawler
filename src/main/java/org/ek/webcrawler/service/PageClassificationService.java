package org.ek.webcrawler.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ek.webcrawler.model.CrawledPage;
import org.ek.webcrawler.repository.CrawledPageRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service til at klassificere crawlede sider med AI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageClassificationService {

    private final GroqService groqService;
    private final CrawledPageRepository pageRepository;

    // Meget kortere prompt (reducerer tokens med ~70%)
    private static final String SYSTEM_PROMPT = """
            Klassificer websiden i ÉN kategori baseret primært på URL-mønsteret:
            
            PRODUCT - Specifikt produkt til salg
            URL: /p/, /product/, /item/, -sku, -id i URL
            Ex: /p/nike-air-max, /product/iphone-15
            
            CATEGORY - Liste af produkter
            URL: /f/produkter/, /category/, /shop/, /collection/
            Ex: /f/produkter/shoes, /shop/electronics
            
            INFORMATION - Guides, ekspertise, læring, vores
            URL: /vores-ekspertise/, /expertise/, /guide/, /learn/, /about/, /info/
            Også: sygdomme/tilstande (/psoriasis/, /eksem/, /akne/)
            Ex: /vores-ekspertise/psoriasis, /guide/skincare, /hvad-er-akne
            
            BLOG - Artikler med dato
            URL: /blog/, /news/, /article/, dato i URL
            
            CONTACT - Kontakt/support
            URL: /contact/, /kontakt/, /support/
            
            JOB - Karriere
            URL: /jobs/, /career/, /karriere/
            
            LEGAL - Juridisk
            URL: /privacy/, /terms/, /cookies/, /legal/
            
            HOME - Forside (kun depth=0)
            
            UNKNOWN - Kun hvis URL ikke matcher
            
            Beslutning: 1) Tjek URL først, 2) Brug titel, 3) Brug indhold
            Undgå UNKNOWN - matcher URL altid et mønster!
            
            Svar KUN med kategorinavn (ex: "INFORMATION")
            """;

    /**
     * Klassificer alle sider
     * Øget delay for at undgå rate limit
     */
    @Async
    public void classifyAllPages(Long jobId) {
        log.info("Starter klassificering for job {}", jobId);

        List<CrawledPage> pages = pageRepository.findByCrawlJobIdAndIsSuccessfulTrue(jobId);
        log.info("Fandt {} succesfulde sider at klassificere", pages.size());

        int classified = 0;
        int failed = 0;

        for (CrawledPage page : pages) {
            try {
                String category = classifyPage(page);
                updatePageCategory(page.getId(), category, null);
                classified++;
                log.debug("Klassificerede side {}: {} -> {}", page.getId(), page.getUrl(), category);
            } catch (Exception e) {
                log.error("Kunne ikke klassificere side {}: {}", page.getId(), e.getMessage());

                // Hvis rate limit, vent længere
                if (e.getMessage().contains("rate_limit") || e.getMessage().contains("429")) {
                    log.warn("Rate limit ramt - venter 2 sekunder...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                updatePageCategory(page.getId(), "UNKNOWN", e.getMessage());
                failed++;
            }

            // Øget delay fra 100ms til 500ms for at undgå rate limit
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Klassificering færdig for job {}. Klassificeret: {}, Fejlet: {}", jobId, classified, failed);
    }

    /**
     * Klassificer en enkelt side
     */
    public String classifyPage(CrawledPage page) throws Exception {
        String userPrompt = buildPrompt(page);
        String response = groqService.getChatCompletion(SYSTEM_PROMPT, userPrompt);

        String category = response.toUpperCase().trim();
        if (!isValidCategory(category)) {
            log.warn("Ugyldig kategori returneret: {}. Bruger UNKNOWN", response);
            return "UNKNOWN";
        }

        return category;
    }

    /**
     * Byg prompt
     * Reduceret tekstindhold fra 1500 til 500 karakterer
     */
    private String buildPrompt(CrawledPage page) {
        StringBuilder prompt = new StringBuilder();

        // URL
        prompt.append("URL: ").append(page.getUrl()).append("\n");

        // Titel
        if (page.getTitle() != null && !page.getTitle().isEmpty()) {
            prompt.append("Titel: ").append(page.getTitle()).append("\n");
        }

        // Depth
        prompt.append("Depth: ").append(page.getHierarchyLevel());
        if (page.getHierarchyLevel() == 0) {
            prompt.append(" (forside)");
        }
        prompt.append("\n");

        // Reduceret tekstindhold fra 1500 til 500 karakterer
        if (page.getTextContent() != null && !page.getTextContent().isEmpty()) {
            String content = page.getTextContent();
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            prompt.append("Indhold: ").append(content).append("\n");
        }

        return prompt.toString();
    }

    @Transactional
    protected void updatePageCategory(Long pageId, String category, String error) {
        CrawledPage page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Side ikke fundet: " + pageId));

        page.setCategory(category);
        page.setAiAnalyzed(true);
        page.setAiAnalysisError(error);

        pageRepository.save(page);
    }

    private boolean isValidCategory(String category) {
        return List.of("PRODUCT", "CATEGORY", "INFORMATION", "BLOG", "CONTACT",
                "JOB", "LEGAL", "HOME", "UNKNOWN").contains(category);
    }

    public ClassificationStats getStats(Long jobId) {
        List<CrawledPage> pages = pageRepository.findByCrawlJobId(jobId);

        long total = pages.size();
        long analyzed = pages.stream().filter(p -> p.getAiAnalyzed() != null && p.getAiAnalyzed()).count();
        long errors = pages.stream().filter(p -> p.getAiAnalysisError() != null).count();

        java.util.Map<String, Long> categoryCount = pages.stream()
                .filter(p -> p.getCategory() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        CrawledPage::getCategory,
                        java.util.stream.Collectors.counting()
                ));

        return ClassificationStats.builder()
                .totalPages(total)
                .analyzedPages(analyzed)
                .errors(errors)
                .categoryBreakdown(categoryCount)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ClassificationStats {
        private long totalPages;
        private long analyzedPages;
        private long errors;
        private java.util.Map<String, Long> categoryBreakdown;
    }
}