package com.citation.service;

import com.citation.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AIService {

    @Value("${ai.service.api-key:}")
    private String apiKey;

    public Mono<List<String>> extractKeywords(String query) {
        log.info("开始提取关键词，输入: {}", query);
        
        return Mono.fromCallable(() -> {
            String cleaned = query.replaceAll("[\\p{P}\\p{S}]", " ");
            
            List<String> keywords = Arrays.stream(cleaned.split("\\s+"))
                    .filter(word -> word.length() >= 2)
                    .filter(word -> !isStopWord(word))
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());
            
            log.info("提取的关键词: {}", keywords);
            return keywords;
        });
    }

    public Mono<List<String>> translateToEnglish(List<String> chineseKeywords) {
        log.info("开始翻译关键词: {}", chineseKeywords);
        
        return Mono.fromCallable(() -> {
            List<String> englishKeywords = chineseKeywords.stream()
                    .map(this::simpleTranslate)
                    .collect(Collectors.toList());
            
            log.info("翻译后的关键词: {}", englishKeywords);
            return englishKeywords;
        });
    }

    private String simpleTranslate(String chinese) {
        switch (chinese) {
            case "机器学习":
                return "machine learning";
            case "深度学习":
                return "deep learning";
            case "人工智能":
                return "artificial intelligence";
            case "神经网络":
                return "neural network";
            case "图像识别":
                return "image recognition";
            case "自然语言处理":
                return "natural language processing";
            case "计算机视觉":
                return "computer vision";
            case "数据挖掘":
                return "data mining";
            default:
                return chinese;
        }
    }

    private boolean isStopWord(String word) {
        List<String> stopWords = Arrays.asList(
                "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
                "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
                "你", "会", "着", "没有", "看", "好", "自己", "这"
        );
        return stopWords.contains(word);
    }
}
