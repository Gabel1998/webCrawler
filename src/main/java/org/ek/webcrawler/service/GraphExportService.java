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
     */
    public GraphData generateGraphData(Long jobId) {
        List<CrawledPage> pages = pageService.getPagesForJob(jobId);

        List<Node> nodes = pages.stream()
                .map(page -> Node.builder()
                        .id(page.getId())
                        .url(page.getUrl())
                        .title(page.getTitle())
                        .level(page.getHierarchyLevel())
                        .group(page.getHierarchyLevel()) // For coloring
                        .value(page.getOutgoingLinksCount() + 1) // Size
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
                .children(children.isEmpty() ? null : children)
                .build();
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
        private List<TreeNode> children;
    }
}