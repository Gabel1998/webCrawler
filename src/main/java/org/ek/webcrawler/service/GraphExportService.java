package org.ek.webcrawler.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ek.webcrawler.model.CrawledPage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphExportService {

    private final PageService pageService;

    /**
     * Generate D3-compatible JSON data for visualization
     * includes category for visual grouping
     */
    public GraphData generateGraphData(Long jobId) {
        List<CrawledPage> pages = pageService.getPagesForJob(jobId);

        List<Node> nodes = pages.stream()
                .map(page -> Node.builder()
                        .id(page.getId())
                        .url(page.getUrl())
                        .title(page.getTitle())
                        .level(page.getHierarchyLevel())
                        .group(page.getHierarchyLevel()) // For coloring by depth
                        .value(page.getOutgoingLinksCount() + 1) // Node size
                        .category(page.getCategory() != null ? page.getCategory() : "UNKNOWN")
                        .build())
                .toList();

        List<Link> links = pages.stream()
                .filter(page -> page.getParentPage() != null)
                .map(page -> Link.builder()
                        .source(page.getParentPage().getId())
                        .target(page.getId())
                        .value(1)
                        .build())
                .toList();

        return GraphData.builder()
                .nodes(nodes)
                .links(links)
                .build();
    }

    /**
     * Generate hierarchical tree structure
     */
    public TreeNode generateTreeData(Long jobId) {
        List<CrawledPage> rootPages = pageService.getRootPages(jobId);
        if (rootPages.isEmpty()) return null;

        CrawledPage root = rootPages.get(0);
        return buildTreeNode(root);
    }

    private TreeNode buildTreeNode(CrawledPage page) {
        List<TreeNode> children = page.getChildPages().stream()
                .map(this::buildTreeNode)
                .toList();

        return TreeNode.builder()
                .name(page.getTitle() != null ? page.getTitle() : page.getUrl())
                .url(page.getUrl())
                .value(page.getOutgoingLinksCount())
                .category(page.getCategory() != null ? page.getCategory() : "UNKNOWN")
                .children(children.isEmpty() ? null : children)
                .build();
    }

    /**
     * Generate GraphML XML format (compatible with Gephi, yEd, etc.)
     */
    public String generateGraphMLXml(Long jobId) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // Root element
            Element graphml = doc.createElement("graphml");
            graphml.setAttribute("xmlns", "http://graphml.graphdrawing.org/xmlns");
            doc.appendChild(graphml);

            // Define node attributes
            Element keyUrl = doc.createElement("key");
            keyUrl.setAttribute("id", "url");
            keyUrl.setAttribute("for", "node");
            keyUrl.setAttribute("attr.name", "url");
            keyUrl.setAttribute("attr.type", "string");
            graphml.appendChild(keyUrl);

            Element keyTitle = doc.createElement("key");
            keyTitle.setAttribute("id", "title");
            keyTitle.setAttribute("for", "node");
            keyTitle.setAttribute("attr.name", "title");
            keyTitle.setAttribute("attr.type", "string");
            graphml.appendChild(keyTitle);

            Element keyLevel = doc.createElement("key");
            keyLevel.setAttribute("id", "level");
            keyLevel.setAttribute("for", "node");
            keyLevel.setAttribute("attr.name", "hierarchyLevel");
            keyLevel.setAttribute("attr.type", "int");
            graphml.appendChild(keyLevel);

            // Graph element
            Element graph = doc.createElement("graph");
            graph.setAttribute("id", "CrawlJob_" + jobId);
            graph.setAttribute("edgedefault", "directed");
            graphml.appendChild(graph);

            // Add nodes
            List<CrawledPage> pages = pageService.getPagesForJob(jobId);
            for (CrawledPage page : pages) {
                Element node = doc.createElement("node");
                node.setAttribute("id", "n" + page.getId());

                Element dataUrl = doc.createElement("data");
                dataUrl.setAttribute("key", "url");
                dataUrl.setTextContent(page.getUrl());
                node.appendChild(dataUrl);

                Element dataTitle = doc.createElement("data");
                dataTitle.setAttribute("key", "title");
                dataTitle.setTextContent(page.getTitle() != null ? page.getTitle() : "");
                node.appendChild(dataTitle);

                Element dataLevel = doc.createElement("data");
                dataLevel.setAttribute("key", "level");
                dataLevel.setTextContent(String.valueOf(page.getHierarchyLevel()));
                node.appendChild(dataLevel);

                graph.appendChild(node);
            }

            // Add edges
            for (CrawledPage page : pages) {
                if (page.getParentPage() != null) {
                    Element edge = doc.createElement("edge");
                    edge.setAttribute("source", "n" + page.getParentPage().getId());
                    edge.setAttribute("target", "n" + page.getId());
                    graph.appendChild(edge);
                }
            }

            return xmlToString(doc);

        } catch (Exception e) {
            log.error("Error generating GraphML XML: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate hierarchical XML representation
     */
    public String generateHierarchicalXml(Long jobId) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // Root element
            Element crawlResult = doc.createElement("crawlResult");
            crawlResult.setAttribute("jobId", jobId.toString());
            doc.appendChild(crawlResult);

            // Get root pages
            List<CrawledPage> rootPages = pageService.getRootPages(jobId);
            for (CrawledPage root : rootPages) {
                Element pageElement = buildXmlPageNode(doc, root);
                crawlResult.appendChild(pageElement);
            }

            return xmlToString(doc);

        } catch (Exception e) {
            log.error("Error generating hierarchical XML: {}", e.getMessage(), e);
            return null;
        }
    }

    private Element buildXmlPageNode(Document doc, CrawledPage page) {
        Element pageElement = doc.createElement("page");
        pageElement.setAttribute("id", page.getId().toString());
        pageElement.setAttribute("level", page.getHierarchyLevel().toString());

        Element url = doc.createElement("url");
        url.setTextContent(page.getUrl());
        pageElement.appendChild(url);

        if (page.getTitle() != null && !page.getTitle().isEmpty()) {
            Element title = doc.createElement("title");
            title.setTextContent(page.getTitle());
            pageElement.appendChild(title);
        }

        Element status = doc.createElement("status");
        status.setTextContent(page.getIsSuccessful() ? "success" : "failed");
        pageElement.appendChild(status);

        if (page.getHttpStatusCode() != null) {
            Element httpStatus = doc.createElement("httpStatusCode");
            httpStatus.setTextContent(page.getHttpStatusCode().toString());
            pageElement.appendChild(httpStatus);
        }

        // Add child pages recursively
        if (!page.getChildPages().isEmpty()) {
            Element children = doc.createElement("children");
            for (CrawledPage child : page.getChildPages()) {
                Element childElement = buildXmlPageNode(doc, child);
                children.appendChild(childElement);
            }
            pageElement.appendChild(children);
        }

        return pageElement;
    }

    /**
     * Generate XML Sitemap format (for SEO/web indexing)
     */
    public String generateSitemapXml(Long jobId) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // Root element with namespace
            Element urlset = doc.createElement("urlset");
            urlset.setAttribute("xmlns", "http://www.sitemaps.org/schemas/sitemap/0.9");
            doc.appendChild(urlset);

            // Get all successful pages
            List<CrawledPage> pages = pageService.getPagesForJob(jobId).stream()
                    .filter(CrawledPage::getIsSuccessful)
                    .toList();

            for (CrawledPage page : pages) {
                Element urlElement = doc.createElement("url");

                Element loc = doc.createElement("loc");
                loc.setTextContent(page.getUrl());
                urlElement.appendChild(loc);

                Element lastmod = doc.createElement("lastmod");
                lastmod.setTextContent(page.getCrawledAt().toString());
                urlElement.appendChild(lastmod);

                // Priority based on hierarchy level (deeper = lower priority)
                Element priority = doc.createElement("priority");
                double priorityValue = Math.max(0.1, 1.0 - (page.getHierarchyLevel() * 0.1));
                priority.setTextContent(String.format("%.1f", priorityValue));
                urlElement.appendChild(priority);

                urlset.appendChild(urlElement);
            }

            return xmlToString(doc);

        } catch (Exception e) {
            log.error("Error generating sitemap XML: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convert XML Document to String
     */
    private String xmlToString(Document doc) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Error converting XML to string: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * DTOS
     */
    @Data
    @Builder
    public static class GraphData {
        private List<Node> nodes;
        private List<Link> links;
    }

    @Data
    @Builder
    public static class Node {
        private Long id;
        private String url;
        private String title;
        private Integer level;
        private Integer group;
        private Integer value;
        private String category;  // AI classification category
    }

    @Data
    @Builder
    public static class Link {
        private Long source;
        private Long target;
        private Integer value;
    }

    @Data
    @Builder
    public static class TreeNode {
        private String name;
        private String url;
        private Integer value;
        private String category;  //  AI classification category
        private List<TreeNode> children;
    }
}