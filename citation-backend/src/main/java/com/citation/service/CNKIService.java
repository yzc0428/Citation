package com.citation.service;

import com.citation.config.CrawlerConfig;
import com.citation.crawler.CNKICrawler;
import com.citation.crawler.SeleniumCNKICrawler;
import com.citation.dto.Citation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

/**
 * 中国知网服务
 * 负责从知网搜索文献
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CNKIService {

    private final CrawlerConfig crawlerConfig;
    private final CNKICrawler jsoupCrawler;
    private final SeleniumCNKICrawler seleniumCrawler;
    private final Random random = new Random();
    
    @Value("${crawler.cnki.use-selenium:true}")
    private boolean useSelenium;

    /**
     * 搜索文献
     * 
     * @param keywords 关键词列表
     * @return 文献列表
     */
    public Mono<List<Citation>> search(List<String> keywords) {
        String crawlerType = useSelenium ? "Selenium" : "Jsoup";
        log.info("知网搜索，关键词: {}, 爬虫模式: {}, 爬虫类型: {}", 
                keywords, crawlerConfig.isEnabled(), crawlerType);
        
        if (crawlerConfig.isEnabled()) {
            // 爬虫模式：真实爬取知网
            return searchViaCrawler(keywords)
                    .onErrorResume(e -> {
                        log.warn("知网爬虫失败，降级到Mock数据: {}", e.getMessage());
                        return searchViaMock(keywords);
                    });
        } else {
            // Mock模式：返回模拟数据
            return searchViaMock(keywords);
        }
    }

    /**
     * 通过爬虫搜索文献
     */
    private Mono<List<Citation>> searchViaCrawler(List<String> keywords) {
        if (useSelenium) {
            // 使用Selenium爬虫
            log.debug("使用Selenium爬虫");
            return Mono.fromCallable(() -> seleniumCrawler.crawl(keywords))
                    .timeout(Duration.ofSeconds(30))  // Selenium需要更长时间
                    .onErrorMap(java.util.concurrent.TimeoutException.class,
                            e -> new RuntimeException("Selenium爬取超时（30秒），请稍后重试", e))
                    .doOnSuccess(citations -> log.info("Selenium爬虫返回 {} 条结果", citations.size()))
                    .doOnError(e -> log.error("Selenium爬虫异常: {}", e.getMessage()));
        } else {
            // 使用Jsoup爬虫
            log.debug("使用Jsoup爬虫");
            return Mono.fromCallable(() -> jsoupCrawler.crawl(keywords))
                    .timeout(Duration.ofSeconds(15))
                    .onErrorMap(java.util.concurrent.TimeoutException.class,
                            e -> new RuntimeException("Jsoup爬取超时（15秒），请稍后重试", e))
                    .doOnSuccess(citations -> log.info("Jsoup爬虫返回 {} 条结果", citations.size()))
                    .doOnError(e -> log.error("Jsoup爬虫异常: {}", e.getMessage()));
        }
    }

    /**
     * 使用Mock数据
     */
    private Mono<List<Citation>> searchViaMock(List<String> keywords) {
        return Mono.fromCallable(() -> {
            List<Citation> citations = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                Citation citation = new Citation();
                citation.setTitle(generateTitle(keywords, i));
                citation.setAuthors(generateAuthors());
                citation.setYear(String.valueOf(2019 + random.nextInt(6)));
                citation.setSource(generateSource());
                citation.setAbstractText(generateAbstract(keywords));
                citation.setCitationCount(random.nextInt(300));
                citation.setDataSource("cnki");
                citation.setUrl("https://www.cnki.net/");
                citations.add(citation);
            }
            
            log.info("知网Mock返回 {} 条结果", citations.size());
            return citations;
        });
    }

    private String generateTitle(List<String> keywords, int index) {
        String keyword = keywords.isEmpty() ? "研究" : keywords.get(0);
        String[] templates = {
            "基于%s的智能系统研究与应用",
            "%s技术综述与发展趋势分析",
            "%s在大数据环境下的应用研究",
            "面向%s的深度学习方法研究",
            "%s关键技术及其应用前景"
        };
        return String.format(templates[index % templates.length], keyword);
    }

    private String generateAuthors() {
        String[][] authors = {
            {"张伟", "李明", "王芳"},
            {"刘洋", "陈静", "赵辉"},
            {"王磊", "张华", "李娜"},
            {"陈建", "刘强", "王丽"},
            {"李军", "张敏", "王勇"}
        };
        String[] selected = authors[random.nextInt(authors.length)];
        return String.join(", ", selected);
    }

    private String generateSource() {
        String[] sources = {
            "计算机学报",
            "软件学报",
            "自动化学报",
            "中国科学：信息科学",
            "电子学报"
        };
        return sources[random.nextInt(sources.length)];
    }

    private String generateAbstract(List<String> keywords) {
        String keyword = keywords.isEmpty() ? "该领域" : keywords.get(0);
        return String.format(
            "本文针对%s进行了深入研究。提出了一种新颖的方法来解决该领域的关键问题。" +
            "通过大量实验验证，该方法在多个数据集上取得了优异的性能表现，" +
            "相比现有方法具有显著优势。研究结果对%s的理论和应用具有重要意义。",
            keyword, keyword
        );
    }
}
