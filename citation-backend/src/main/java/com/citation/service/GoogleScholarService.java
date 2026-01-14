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
 * 谷歌学术服务
 * 负责从谷歌学术搜索文献
 */
@Slf4j
@Service
public class GoogleScholarService {

    @Value("${academic.google-scholar.api-key:}")
    private String apiKey;

    @Value("${academic.google-scholar.endpoint:}")
    private String endpoint;

    private final Random random = new Random();

    /**
     * 搜索文献
     * 
     * @param keywords 关键词列表
     * @return 文献列表
     */
    public Mono<List<Citation>> search(List<String> keywords) {
        log.info("谷歌学术搜索，关键词: {}", keywords);
        
        // 生成模拟数据用于演示
        // 实际项目中应该调用真实的谷歌学术 API（如 SerpAPI）
        return Mono.fromCallable(() -> {
            List<Citation> citations = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                Citation citation = new Citation();
                citation.setTitle(generateTitle(keywords, i));
                citation.setAuthors(generateAuthors());
                citation.setYear(String.valueOf(2020 + random.nextInt(5)));
                citation.setSource(generateSource());
                citation.setAbstractText(generateAbstract(keywords));
                citation.setCitationCount(random.nextInt(500));
                citation.setDataSource("google-scholar");
                citation.setUrl("https://scholar.google.com/scholar?q=" + String.join("+", keywords));
                citations.add(citation);
            }
            
            log.info("谷歌学术返回 {} 条结果", citations.size());
            return citations;
        });
    }

    private String generateTitle(List<String> keywords, int index) {
        String keyword = keywords.isEmpty() ? "Research" : keywords.get(0);
        String[] templates = {
            "A Comprehensive Survey on %s: Methods and Applications",
            "Deep Learning Approaches for %s: A Systematic Review",
            "Recent Advances in %s: Challenges and Opportunities",
            "%s in the Era of Big Data: Trends and Future Directions",
            "Novel %s Techniques: Theory and Practice"
        };
        return String.format(templates[index % templates.length], keyword);
    }

    private String generateAuthors() {
        String[][] authors = {
            {"Zhang Wei", "Li Ming", "Wang Fang"},
            {"John Smith", "Mary Johnson", "David Brown"},
            {"Chen Jian", "Liu Yang", "Zhao Hui"},
            {"Sarah Williams", "Michael Davis", "Jennifer Wilson"},
            {"Wang Lei", "Zhang Hua", "Li Na"}
        };
        String[] selected = authors[random.nextInt(authors.length)];
        return String.join(", ", selected);
    }

    private String generateSource() {
        String[] sources = {
            "IEEE Transactions on Pattern Analysis and Machine Intelligence",
            "Nature Machine Intelligence",
            "Journal of Machine Learning Research",
            "ACM Computing Surveys",
            "Artificial Intelligence Review"
        };
        return sources[random.nextInt(sources.length)];
    }

    private String generateAbstract(List<String> keywords) {
        String keyword = keywords.isEmpty() ? "the field" : keywords.get(0);
        return String.format(
            "This paper presents a comprehensive study on %s. " +
            "We propose a novel approach that addresses the key challenges in this domain. " +
            "Our method demonstrates significant improvements over existing techniques, " +
            "achieving state-of-the-art performance on multiple benchmark datasets. " +
            "Experimental results validate the effectiveness of our approach.",
            keyword
        );
    }
}
