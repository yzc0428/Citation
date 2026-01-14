package com.citation.crawler;

import com.citation.config.CrawlerConfig;
import com.citation.dto.Citation;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 知网爬虫
 * 负责从知网爬取文献数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CNKICrawler {

    private final CrawlerConfig config;
    private final RateLimiter rateLimiter;

    /**
     * 爬取知网文献
     *
     * @param keywords 关键词列表
     * @return 文献列表
     * @throws IOException 网络异常
     */
    public List<Citation> crawl(List<String> keywords) throws IOException {
        // 限流：等待获取许可
        rateLimiter.acquire();
        log.info("开始爬取知网，关键词: {}", keywords);

        // 构建搜索URL
        String url = buildSearchUrl(keywords);
        log.debug("知网搜索URL: {}", url);

        // 爬取HTML
        Document doc = fetchHtml(url);

        // 解析文献数据
        List<Citation> citations = parseDocument(doc, keywords);
        log.info("知网爬取完成，返回 {} 条结果", citations.size());

        return citations;
    }

    /**
     * 构建知网搜索URL
     */
    private String buildSearchUrl(List<String> keywords) {
        String query = String.join(" ", keywords);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        // 知网搜索URL格式
        // 注意：这是简化的URL，实际知网URL可能更复杂
        return "https://kns.cnki.net/kns8/defaultresult/index?kw=" + encodedQuery + "&korder=SU";
    }

    /**
     * 爬取HTML页面
     */
    private Document fetchHtml(String url) throws IOException {
        try {
            return Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .timeout(config.getTimeout())
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .get();
        } catch (IOException e) {
            log.error("爬取知网失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 解析HTML文档，提取文献信息
     */
    private List<Citation> parseDocument(Document doc, List<String> keywords) {
        List<Citation> citations = new ArrayList<>();

        try {
            // 知网搜索结果的CSS选择器
            // 注意：这些选择器需要根据实际知网页面结构调整
            Elements items = doc.select("table.result-table-list tbody tr");
            
            if (items.isEmpty()) {
                log.warn("未找到搜索结果，可能页面结构已变化或需要登录");
                // 尝试其他可能的选择器
                items = doc.select("tr.odd, tr.even");
            }

            for (Element item : items) {
                try {
                    Citation citation = extractCitation(item, keywords);
                    if (citation != null && citation.getTitle() != null) {
                        citations.add(citation);
                        
                        // 最多返回10条
                        if (citations.size() >= 10) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析单条文献失败: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("解析知网页面失败: {}", e.getMessage(), e);
        }

        return citations;
    }

    /**
     * 从HTML元素中提取文献信息
     */
    private Citation extractCitation(Element item, List<String> keywords) {
        Citation citation = new Citation();

        try {
            // 提取标题
            String title = extractTitle(item);
            if (title == null || title.trim().isEmpty()) {
                return null;
            }
            citation.setTitle(title);

            // 提取作者
            String authors = extractAuthors(item);
            citation.setAuthors(authors);

            // 提取年份
            String year = extractYear(item);
            citation.setYear(year);

            // 提取来源（期刊/会议）
            String source = extractSource(item);
            citation.setSource(source);

            // 提取摘要
            String abstractText = extractAbstract(item);
            citation.setAbstractText(abstractText);

            // 提取引用次数
            Integer citationCount = extractCitationCount(item);
            citation.setCitationCount(citationCount);

            // 提取URL
            String url = extractUrl(item);
            citation.setUrl(url);

            // 设置数据源
            citation.setDataSource("cnki");

        } catch (Exception e) {
            log.warn("提取文献字段失败: {}", e.getMessage());
        }

        return citation;
    }

    /**
     * 提取标题
     */
    private String extractTitle(Element item) {
        // 尝试多个可能的选择器
        Element titleElement = item.selectFirst("a.fz14");
        if (titleElement == null) {
            titleElement = item.selectFirst("td.name a");
        }
        if (titleElement == null) {
            titleElement = item.selectFirst("a[href*=detail]");
        }
        
        return titleElement != null ? titleElement.text().trim() : null;
    }

    /**
     * 提取作者
     */
    private String extractAuthors(Element item) {
        Element authorsElement = item.selectFirst("td.author");
        if (authorsElement == null) {
            authorsElement = item.selectFirst("a[href*=author]");
        }
        
        if (authorsElement != null) {
            return authorsElement.text().trim();
        }
        
        // 默认值
        return "未知作者";
    }

    /**
     * 提取年份
     */
    private String extractYear(Element item) {
        Element dateElement = item.selectFirst("td.date");
        if (dateElement == null) {
            // 尝试从文本中提取年份
            String text = item.text();
            if (text.matches(".*\\d{4}.*")) {
                String year = text.replaceAll(".*?(\\d{4}).*", "$1");
                if (year.matches("\\d{4}")) {
                    return year;
                }
            }
        } else {
            String dateText = dateElement.text().trim();
            if (dateText.matches(".*\\d{4}.*")) {
                return dateText.replaceAll(".*?(\\d{4}).*", "$1");
            }
        }
        
        return "2024"; // 默认当前年份
    }

    /**
     * 提取来源（期刊/会议）
     */
    private String extractSource(Element item) {
        Element sourceElement = item.selectFirst("td.source");
        if (sourceElement == null) {
            sourceElement = item.selectFirst("a[href*=journal]");
        }
        
        if (sourceElement != null) {
            return sourceElement.text().trim();
        }
        
        return "未知来源";
    }

    /**
     * 提取摘要
     */
    private String extractAbstract(Element item) {
        Element abstractElement = item.selectFirst("td.abstract");
        if (abstractElement == null) {
            abstractElement = item.selectFirst("span.abstract");
        }
        
        if (abstractElement != null) {
            String abstractText = abstractElement.text().trim();
            // 限制摘要长度
            if (abstractText.length() > 300) {
                abstractText = abstractText.substring(0, 297) + "...";
            }
            return abstractText;
        }
        
        // 如果没有摘要，生成一个简单的描述
        return "本文对相关主题进行了研究和分析。";
    }

    /**
     * 提取引用次数
     */
    private Integer extractCitationCount(Element item) {
        Element citationElement = item.selectFirst("td.quote");
        if (citationElement == null) {
            citationElement = item.selectFirst("span.quote");
        }
        
        if (citationElement != null) {
            String text = citationElement.text().trim();
            try {
                // 提取数字
                String number = text.replaceAll("\\D+", "");
                if (!number.isEmpty()) {
                    return Integer.parseInt(number);
                }
            } catch (NumberFormatException e) {
                log.debug("解析引用次数失败: {}", text);
            }
        }
        
        return 0; // 默认0
    }

    /**
     * 提取URL
     */
    private String extractUrl(Element item) {
        Element linkElement = item.selectFirst("a[href]");
        if (linkElement != null) {
            String href = linkElement.attr("href");
            if (href.startsWith("http")) {
                return href;
            } else if (href.startsWith("/")) {
                return "https://kns.cnki.net" + href;
            }
        }
        
        return "https://kns.cnki.net/";
    }
}
