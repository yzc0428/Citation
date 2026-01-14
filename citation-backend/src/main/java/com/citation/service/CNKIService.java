package com.citation.service;

import com.citation.dto.Citation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国知网服务
 * 负责从知网搜索文献
 */
@Slf4j
@Service
public class CNKIService {

    @Value("${academic.cnki.api-key:}")
    private String apiKey;

    @Value("${academic.cnki.endpoint:}")
    private String endpoint;

    private final Random random = new Random();

    /**
     * 搜索文献
     * 
     * @param keywords 关键词列表
     * @return 文献列表
     */
    public Mono<List<Citation>> search(List<String> keywords) {
        log.info("知网搜索，关键词: {}", keywords);
        
        // 生成模拟数据用于演示
        // 实际项目中应该调用真实的知网 API
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
            
            log.info("知网返回 {} 条结果", citations.size());
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
