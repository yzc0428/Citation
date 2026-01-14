package com.citation.crawler;

import com.citation.dto.Citation;
import com.google.common.util.concurrent.RateLimiter;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 谷歌学术镜像爬虫
 * 使用Selenium爬取谷歌学术镜像站点
 */
@Slf4j
@Component
public class GoogleScholarCrawler {

    private final RateLimiter rateLimiter;
    private final int timeout;
    private final String userAgent;
    
    @Value("${crawler.google-scholar.mirror-url:https://www.defineabc.com}")
    private String mirrorUrl;
    
    // 备用镜像站点
    private final List<String> fallbackMirrors = Arrays.asList(
        "https://www.defineabc.com",
        "https://scholar.lanfanshu.cn",
        "https://xs.dailyheadlines.cc",
        "https://sc.panda321.com"
    );

    public GoogleScholarCrawler(RateLimiter cnkiRateLimiter) {
        this.rateLimiter = cnkiRateLimiter;
        this.timeout = 15;
        this.userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    @PostConstruct
    public void init() {
        log.info("初始化Google Scholar爬虫，镜像站点: {}", mirrorUrl);
        try {
            WebDriverManager.chromedriver().setup();
            log.info("ChromeDriver已就绪");
        } catch (Exception e) {
            log.error("ChromeDriver初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 创建WebDriver实例
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=" + userAgent);
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--remote-allow-origins=*");
        options.setAcceptInsecureCerts(true);
        
        log.debug("创建ChromeDriver实例");
        return new ChromeDriver(options);
    }

    /**
     * 爬取谷歌学术
     */
    public List<Citation> crawl(List<String> keywords) {
        rateLimiter.acquire();
        log.info("开始爬取Google Scholar，关键词: {}", keywords);

        WebDriver driver = null;
        try {
            driver = createDriver();
            
            // 尝试主镜像站点
            List<Citation> citations = crawlWithMirror(driver, mirrorUrl, keywords);
            
            // 如果主镜像失败，尝试备用镜像
            if (citations.isEmpty()) {
                for (String fallbackMirror : fallbackMirrors) {
                    if (!fallbackMirror.equals(mirrorUrl)) {
                        log.info("尝试备用镜像: {}", fallbackMirror);
                        citations = crawlWithMirror(driver, fallbackMirror, keywords);
                        if (!citations.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            
            log.info("Google Scholar爬取完成，返回 {} 条结果", citations.size());
            return citations;

        } catch (Exception e) {
            log.error("Google Scholar爬取失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    log.debug("WebDriver已关闭");
                } catch (Exception e) {
                    log.warn("关闭WebDriver失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 使用指定镜像站点爬取
     */
    private List<Citation> crawlWithMirror(WebDriver driver, String mirror, List<String> keywords) {
        try {
            String searchUrl = buildSearchUrl(mirror, keywords);
            log.debug("访问URL: {}", searchUrl);

            driver.get(searchUrl);
            
            // 等待搜索结果加载
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout));
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.className("gs_ri")),
                ExpectedConditions.presenceOfElementLocated(By.id("gs_res_ccl_mid"))
            ));
            
            log.debug("页面加载完成");
            
            // 解析结果
            return parsePage(driver, keywords);
            
        } catch (Exception e) {
            log.warn("镜像 {} 爬取失败: {}", mirror, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(String mirror, List<String> keywords) {
        String query = String.join("+", keywords);
        return String.format("%s/scholar?hl=en&q=%s", mirror, query);
    }

    /**
     * 解析页面
     */
    private List<Citation> parsePage(WebDriver driver, List<String> keywords) {
        List<Citation> citations = new ArrayList<>();

        try {
            // 查找所有结果项
            List<WebElement> items = driver.findElements(By.className("gs_ri"));
            
            if (items.isEmpty()) {
                log.warn("未找到搜索结果");
                return citations;
            }

            log.debug("找到 {} 个搜索结果", items.size());

            // 最多返回10条
            int count = Math.min(items.size(), 10);
            
            for (int i = 0; i < count; i++) {
                try {
                    WebElement item = items.get(i);
                    Citation citation = extractCitation(item, keywords);
                    if (citation != null) {
                        citations.add(citation);
                    }
                } catch (Exception e) {
                    log.warn("解析第 {} 条结果失败: {}", i + 1, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("解析页面失败: {}", e.getMessage(), e);
        }

        return citations;
    }

    /**
     * 提取单条文献信息
     */
    private Citation extractCitation(WebElement item, List<String> keywords) {
        try {
            Citation citation = new Citation();

            // 提取标题和URL
            WebElement titleElement = item.findElement(By.className("gs_rt"));
            WebElement titleLink = titleElement.findElement(By.tagName("a"));
            String title = titleLink.getText().trim();
            String url = titleLink.getAttribute("href");
            
            if (title.isEmpty()) {
                return null;
            }
            
            citation.setTitle(title);
            citation.setUrl(url != null ? url : "");

            // 提取作者、年份、来源信息（在 gs_a 中）
            try {
                WebElement authorElement = item.findElement(By.className("gs_a"));
                String authorInfo = authorElement.getText();
                parseAuthorInfo(citation, authorInfo);
            } catch (Exception e) {
                log.debug("提取作者信息失败: {}", e.getMessage());
                citation.setAuthors("Unknown");
                citation.setYear("2024");
                citation.setSource("Google Scholar");
            }

            // 提取摘要
            try {
                WebElement abstractElement = item.findElement(By.className("gs_rs"));
                String abstractText = abstractElement.getText().trim();
                citation.setAbstractText(abstractText);
            } catch (Exception e) {
                citation.setAbstractText("No abstract available.");
            }

            // 提取引用次数
            try {
                List<WebElement> links = item.findElements(By.cssSelector(".gs_fl a"));
                int citationCount = 0;
                for (WebElement link : links) {
                    String linkText = link.getText();
                    if (linkText.contains("引用") || linkText.contains("被引")) {
                        citationCount = parseCitationCount(linkText);
                        break;
                    }
                }
                citation.setCitationCount(citationCount);
            } catch (Exception e) {
                citation.setCitationCount(0);
            }

            // 设置数据源
            citation.setDataSource("google-scholar");

            log.debug("成功提取文献: {}", title);
            return citation;

        } catch (Exception e) {
            log.warn("提取文献信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析作者信息字符串
     * 格式通常为: "作者1, 作者2 - 来源, 年份"
     */
    private void parseAuthorInfo(Citation citation, String authorInfo) {
        try {
            // 分割作者和来源
            String[] parts = authorInfo.split(" - ");
            
            if (parts.length > 0) {
                // 提取作者
                String authors = parts[0].trim();
                citation.setAuthors(authors);
            }
            
            if (parts.length > 1) {
                // 提取来源和年份
                String sourceAndYear = parts[1].trim();
                
                // 提取年份（4位数字）
                Pattern yearPattern = Pattern.compile("(\\d{4})");
                Matcher yearMatcher = yearPattern.matcher(sourceAndYear);
                if (yearMatcher.find()) {
                    citation.setYear(yearMatcher.group(1));
                    // 移除年份后剩下的是来源
                    String source = sourceAndYear.replaceAll("\\d{4}", "").replaceAll(",\\s*$", "").trim();
                    citation.setSource(source.isEmpty() ? "Google Scholar" : source);
                } else {
                    citation.setYear("2024");
                    citation.setSource(sourceAndYear);
                }
            } else {
                citation.setYear("2024");
                citation.setSource("Google Scholar");
            }
            
        } catch (Exception e) {
            log.debug("解析作者信息失败: {}", e.getMessage());
            citation.setAuthors("Unknown");
            citation.setYear("2024");
            citation.setSource("Google Scholar");
        }
    }

    /**
     * 解析引用次数
     */
    private int parseCitationCount(String text) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("解析引用次数失败: {}", e.getMessage());
        }
        return 0;
    }

    @PreDestroy
    public void cleanup() {
        log.info("清理Google Scholar爬虫资源...");
    }
}
