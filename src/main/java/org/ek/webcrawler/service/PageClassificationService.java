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
 * Kører asynkront for ikke at blokere API'et
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageClassificationService {

    private final GroqService groqService;
    private final CrawledPageRepository pageRepository;

    // ✅ FORBEDRET: Mere præcis system prompt med eksempler
    private static final String SYSTEM_PROMPT = """
            Du er en ekspert i at klassificere websider baseret på deres indhold og URL.
            
            Klassificer websiden i ÉN af følgende kategorier:
            
            - PRODUCT: En side der præsenterer ET specifikt produkt til salg
              Eksempler: "iPhone 15 Pro", "Nike Air Max", "Coloplast SenSura Mio"
              Kendetegn: Pris, køb-knap, produktbilleder, produkt-specs
              URL mønstre: /product/, /p/, /item/, SKU/produkt-ID i URL
            
            - CATEGORY: En liste/oversigt der viser FLERE produkter eller produktkategorier
              Eksempler: "Se alle smartphones", "Hudpleje produkter", "Mænd > Sko > Sneakers"
              Kendetegn: Grid/liste med produkter, filtre, sorteringsmuligheder
              URL mønstre: /category/, /collection/, /shop/, /products/
              VIGTIGT: Kun hvis siden primært lister produkter!
            
            - INFORMATION: Informationssider med guidende eller uddannende indhold
              Eksempler: "Hvad er akne?", "Hvordan virker stomi?", "Guide til hudpleje"
              Kendetegn: Artikellignende indhold, forklarende tekst, guides, FAQ
              URL mønstre: /info/, /guide/, /learn/, /about/, /help/, /faq/
              VIGTIGT: Omfatter også sider om sygdomme, tilstande, behandlinger
            
            - BLOG: Blog indlæg, nyheder eller artikler med dato
              Eksempler: "5 tips til bedre hud", "Nye produkter i 2024"
              Kendetegn: Publiceringsdato, forfatter, nyhedsformat
              URL mønstre: /blog/, /news/, /article/, dato i URL
            
            - CONTACT: Kontakt, support eller kundeservice
              Eksempler: "Kontakt os", "Kundeservice", "Find forhandler"
              Kendetegn: Kontaktformular, telefonnumre, email, chat
              URL mønstre: /contact/, /support/, /help/, /customer-service/
            
            - JOB: Job, karriere eller rekruttering
              Eksempler: "Ledige stillinger", "Bliv en del af teamet", "Vi søger"
              URL mønstre: /jobs/, /careers/, /career/, /join-us/
            
            - LEGAL: Juridiske dokumenter
              Eksempler: "Privatlivspolitik", "Cookies", "Handelsbetingelser"
              URL mønstre: /privacy/, /terms/, /legal/, /cookies/
            
            - HOME: Forside eller hovedlandingsside (kun hvis depth = 0)
              Kendetegn: Velkomst, hero-banner, oversigt over sektioner
            
            - UNKNOWN: Kun hvis du virkelig ikke kan bestemme kategorien
            
            VIGTIGE REGLER:
            1. En side om "hvad er X sygdom/tilstand" er INFORMATION, IKKE CATEGORY
            2. En side der forklarer/uddanner er INFORMATION
            3. En side der lister/viser produkter til køb er CATEGORY
            4. CATEGORY skal have FLERE produkter, ikke bare info om ét emne
            5. Hvis i tvivl mellem INFORMATION og CATEGORY: Vælg INFORMATION hvis siden primært forklarer/uddanner
            
            Svar KUN med kategorinavnet (f.eks. "INFORMATION") - ingen forklaring.
            """;

    /**
     * Klassificer alle sider i et crawl job asynkront
     * Kaldes fra CrawlJobController
     *
     * @param jobId ID på crawl jobbet
     */
    @Async
    public void classifyAllPages(Long jobId) {
        log.info("Starter klassificering for job {}", jobId);

        // Hent kun succesfulde sider (ignorer fejlede crawls)
        List<CrawledPage> pages = pageRepository.findByCrawlJobIdAndIsSuccessfulTrue(jobId);
        log.info("Fandt {} succesfulde sider at klassificere", pages.size());

        int classified = 0;
        int failed = 0;

        // Klassificer hver side
        for (CrawledPage page : pages) {
            try {
                String category = classifyPage(page);
                updatePageCategory(page.getId(), category, null);
                classified++;
                log.debug("Klassificerede side {}: {} -> {}", page.getId(), page.getUrl(), category);
            } catch (Exception e) {
                log.error("Kunne ikke klassificere side {}: {}", page.getId(), e.getMessage());
                updatePageCategory(page.getId(), "UNKNOWN", e.getMessage());
                failed++;
            }

            // Lille delay for at undgå rate limiting
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Klassificering færdig for job {}. Klassificeret: {}, Fejlet: {}", jobId, classified, failed);
    }

    /**
     * Klassificer en enkelt side ved at kalde Groq API
     *
     * @param page Siden der skal klassificeres
     * @return Kategori navn (PRODUCT, INFORMATION, etc.)
     */
    public String classifyPage(CrawledPage page) throws Exception {
        String userPrompt = buildPrompt(page);
        String response = groqService.getChatCompletion(SYSTEM_PROMPT, userPrompt);

        // Valider at svaret er en gyldig kategori
        String category = response.toUpperCase().trim();
        if (!isValidCategory(category)) {
            log.warn("Ugyldig kategori returneret: {}. Bruger UNKNOWN", response);
            return "UNKNOWN";
        }

        return category;
    }

    /**
     * Byg prompt til AI'en baseret på side data
     * Inkluderer URL, titel, hierarki niveau OG tekstindhold
     */
    private String buildPrompt(CrawledPage page) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("URL: ").append(page.getUrl()).append("\n");

        if (page.getTitle() != null && !page.getTitle().isEmpty()) {
            prompt.append("Titel: ").append(page.getTitle()).append("\n");
        }

        // Tilføj hierarki kontekst (hjælper med at identificere forside)
        prompt.append("Dybde: ").append(page.getHierarchyLevel()).append("\n");

        if (page.getHierarchyLevel() == 0) {
            prompt.append("(Dette er forsiden)\n");
        }

        // KRITISK: Tilføj tekstindhold hvis tilgængeligt
        if (page.getTextContent() != null && !page.getTextContent().isEmpty()) {
            // Begræns til første 1000 karakterer for at spare tokens
            String content = page.getTextContent();
            if (content.length() > 1000) {
                content = content.substring(0, 1000) + "...";
            }
            prompt.append("\nIndhold:\n").append(content).append("\n");
        } else {
            log.warn("Ingen tekstindhold for side {}, klassificering bliver mindre præcis", page.getId());
        }

        return prompt.toString();
    }

    /**
     * Opdater kategori i databasen
     * Gemmer også evt. fejl og markerer som analyseret
     */
    @Transactional
    protected void updatePageCategory(Long pageId, String category, String error) {
        CrawledPage page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Side ikke fundet: " + pageId));

        page.setCategory(category);
        page.setAiAnalyzed(true);
        page.setAiAnalysisError(error);

        pageRepository.save(page);
    }

    /**
     * Check om kategori er gyldig
     */
    private boolean isValidCategory(String category) {
        return List.of("PRODUCT", "CATEGORY", "INFORMATION", "BLOG", "CONTACT",
                "JOB", "LEGAL", "HOME", "UNKNOWN").contains(category);
    }

    /**
     * Hent klassificerings statistik for et job
     * Bruges til at vise fremskridt og fordeling
     *
     * @param jobId ID på crawl jobbet
     * @return Statistik objekt med total, analyserede og kategori fordeling
     */
    public ClassificationStats getStats(Long jobId) {
        List<CrawledPage> pages = pageRepository.findByCrawlJobId(jobId);

        long total = pages.size();
        long analyzed = pages.stream().filter(p -> p.getAiAnalyzed() != null && p.getAiAnalyzed()).count();
        long errors = pages.stream().filter(p -> p.getAiAnalysisError() != null).count();

        // Tæl antal sider per kategori
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

    /**
     * DTO til statistik response
     */
    @lombok.Data
    @lombok.Builder
    public static class ClassificationStats {
        private long totalPages;        // Total antal sider i jobbet
        private long analyzedPages;     // Antal analyserede sider
        private long errors;            // Antal fejl under klassificering
        private java.util.Map<String, Long> categoryBreakdown;  // Antal per kategori
    }
}