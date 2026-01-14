package com.citation.controller;

import com.citation.dto.SearchRequest;
import com.citation.dto.SearchResponse;
import com.citation.service.SearchService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 搜索控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 搜索文献
     * 
     * @param request 搜索请求
     * @return 搜索响应
     */
    @PostMapping
    public Mono<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        log.info("收到搜索请求: {}", request.getQuery());
        return searchService.search(request);
    }
}
