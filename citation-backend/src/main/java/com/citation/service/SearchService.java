package com.citation.service;

import com.citation.dto.Citation;
import com.citation.dto.SearchRequest;
import com.citation.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索服务
 * 负责协调整个搜索流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final AIService aiService;
    private final GoogleScholarService googleScholarService;
    private final CNKIService cnkiService;

    /**
     * 执行搜索
     * 
     * @param request 搜索请求
     * @return 搜索响应
     */
    public Mono<SearchResponse> search(SearchRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("开始搜索，查询: {}", request.getQuery());

        return aiService.extractKeywords(request.getQuery())
                .flatMap(keywords -> {
                    log.info("提取的关键词: {}", keywords);
                    
                    // 并行搜索谷歌学术和知网，缩短超时时间以快速响应
                    Mono<List<Citation>> googleResults = googleScholarService.search(keywords)
                            .timeout(Duration.ofSeconds(20))
                            .onErrorResume(e -> {
                                log.error("谷歌学术搜索失败: {}", e.getMessage());
                                return Mono.just(List.of());
                            });
                    
                    Mono<List<Citation>> cnkiResults = cnkiService.search(keywords)
                            .timeout(Duration.ofSeconds(20))
                            .onErrorResume(e -> {
                                log.error("知网搜索失败: {}", e.getMessage());
                                return Mono.just(List.of());
                            });
                    
                    // 合并结果
                    return Mono.zip(googleResults, cnkiResults, Mono.just(keywords))
                            .flatMap(tuple -> {
                                // 使用响应式方式合并列表
                                return Flux.concat(
                                        Flux.fromIterable(tuple.getT1()),
                                        Flux.fromIterable(tuple.getT2())
                                )
                                .collectList()
                                .map(allCitations -> {
                                    // 计算相关度并排序
                                    allCitations.forEach(citation -> 
                                        citation.setRelevanceScore(calculateRelevance(citation, tuple.getT3()))
                                    );
                                    
                                    List<Citation> sortedCitations = allCitations.stream()
                                            .sorted(Comparator.comparing(Citation::getRelevanceScore).reversed())
                                            .limit(10)
                                            .collect(Collectors.toList());
                                    
                                    SearchResponse response = new SearchResponse();
                                    response.setSuccess(true);
                                    
                                    // 根据结果数量设置不同的消息
                                    if (sortedCitations.isEmpty()) {
                                        response.setMessage("搜索完成，但未找到相关文献。建议尝试其他关键词。");
                                    } else {
                                        response.setMessage(String.format("搜索成功，找到 %d 条相关文献", sortedCitations.size()));
                                    }
                                    
                                    response.setKeywords(tuple.getT3());
                                    response.setCitations(sortedCitations);
                                    response.setDuration(System.currentTimeMillis() - startTime);
                                    
                                    log.info("搜索完成，返回 {} 条结果，耗时 {}ms", 
                                            sortedCitations.size(), response.getDuration());
                                    
                                    return response;
                                });
                            });
                })
                .timeout(Duration.ofSeconds(45))
                .onErrorResume(e -> {
                    log.error("搜索超时或失败: {}", e.getMessage());
                    SearchResponse errorResponse = new SearchResponse();
                    errorResponse.setSuccess(false);
                    errorResponse.setMessage("搜索超时，请稍后重试或使用更具体的关键词");
                    errorResponse.setCitations(List.of());
                    errorResponse.setDuration(System.currentTimeMillis() - startTime);
                    return Mono.just(errorResponse);
                });
    }

    /**
     * 计算文献相关度评分
     * 
     * @param citation 文献
     * @param keywords 关键词列表
     * @return 相关度评分（0-100）
     */
    private Double calculateRelevance(Citation citation, List<String> keywords) {
        double score = 0.0;
        
        String title = citation.getTitle() != null ? citation.getTitle().toLowerCase() : "";
        String abstractText = citation.getAbstractText() != null ? citation.getAbstractText().toLowerCase() : "";
        
        // 标题匹配（权重 40%）
        for (String keyword : keywords) {
            if (title.contains(keyword.toLowerCase())) {
                score += 40.0 / keywords.size();
            }
        }
        
        // 摘要匹配（权重 30%）
        for (String keyword : keywords) {
            if (abstractText.contains(keyword.toLowerCase())) {
                score += 30.0 / keywords.size();
            }
        }
        
        // 引用次数（权重 20%）
        if (citation.getCitationCount() != null && citation.getCitationCount() > 0) {
            score += Math.min(20.0, citation.getCitationCount() / 10.0);
        }
        
        // 发表年份（权重 10%，越新越高）
        if (citation.getYear() != null) {
            try {
                int year = Integer.parseInt(citation.getYear());
                int currentYear = java.time.Year.now().getValue();
                if (year >= currentYear - 5) {
                    score += 10.0;
                } else if (year >= currentYear - 10) {
                    score += 5.0;
                }
            } catch (NumberFormatException e) {
                // 忽略年份解析错误
            }
        }
        
        return Math.min(100.0, score);
    }
}
