package com.citation.service;

import com.citation.crawler.GoogleScholarCrawler;
import com.citation.dto.Citation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Google Scholar服务
 * 负责调用真实爬虫获取文献数据
 */
@Slf4j
@Service
public class GoogleScholarService {

    private final GoogleScholarCrawler crawler;

    public GoogleScholarService(GoogleScholarCrawler crawler) {
        this.crawler = crawler;
    }

    /**
     * 搜索文献 - 仅使用真实爬虫
     */
    public Mono<List<Citation>> search(List<String> keywords) {
        log.info("Google Scholar搜索，关键词: {}", keywords);
        
        return Mono.fromCallable(() -> crawler.crawl(keywords))
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(citations -> {
                    if (citations.isEmpty()) {
                        log.warn("Google Scholar爬虫返回空结果");
                    } else {
                        log.info("Google Scholar爬虫成功返回 {} 条结果", citations.size());
                    }
                })
                .doOnError(e -> log.error("Google Scholar爬虫失败: {}", e.getMessage()));
    }
}
