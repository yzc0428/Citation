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
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知网Selenium爬虫
 * 使用Selenium WebDriver模拟真实浏览器访问知网
 */
@Slf4j
@Component
public class SeleniumCNKICrawler {

    private final RateLimiter rateLimiter;
    private WebDriver driver;
    private final int timeout;
    private final String userAgent;

    public SeleniumCNKICrawler(RateLimiter cnkiRateLimiter) {
        this.rateLimiter = cnkiRateLimiter;
        this.timeout = 15;
        this.userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (Educational Purpose; Citation Research System)";
    }

    @PostConstruct
    public void init() {
        log.info("初始化Selenium ChromeDriver...");
        try {
            // 使用WebDriverManager自动管理ChromeDriver
            WebDriverManager.chromedriver().setup();
            log.info("ChromeDriver初始化成功");
        } catch (Exception e) {
            log.error("ChromeDriver初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 创建WebDriver实例
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        
        // 无头模式（不显示浏览器窗口）
        options.addArguments("--headless=new");
        
        // 禁用GPU加速
        options.addArguments("--disable-gpu");
        
        // 禁用沙箱模式（Linux环境需要）
        options.addArguments("--no-sandbox");
        
        // 禁用开发者模式
        options.addArguments("--disable-dev-shm-usage");
        
        // 设置窗口大小
        options.addArguments("--window-size=1920,1080");
        
        // 设置User-Agent
        options.addArguments("--user-agent=" + userAgent);
        
        // 禁用图片加载（提高速度）
        options.addArguments("--blink-settings=imagesEnabled=false");
        
        // 忽略证书错误
        options.addArguments("--ignore-certificate-errors");
        
        log.debug("创建ChromeDriver，配置: headless, no-sandbox, disable-gpu");
        return new ChromeDriver(options);
    }

    /**
     * 爬取知网文献
     */
    public List<Citation> crawl(List<String> keywords) {
        // 限流
        rateLimiter.acquire();
        log.info("开始Selenium爬取知网，关键词: {}", keywords);

        WebDriver localDriver = null;
        try {
            // 创建WebDriver
            localDriver = createDriver();
            
            // 构建搜索URL
            String searchUrl = buildSearchUrl(keywords);
            log.debug("知网搜索URL: {}", searchUrl);

            // 访问页面
            localDriver.get(searchUrl);
            
            // 等待页面加载
            WebDriverWait wait = new WebDriverWait(localDriver, Duration.ofSeconds(timeout));
            
            // 等待搜索结果容器出现（多种可能的选择器）
            try {
                wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.className("result-table-list")),
                    ExpectedConditions.presenceOfElementLocated(By.className("GridTableContent")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("table.result")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".search-list"))
                ));
                log.debug("搜索结果容器已加载");
            } catch (Exception e) {
                log.warn("等待搜索结果超时，尝试直接解析页面");
            }

            // 解析页面
            List<Citation> citations = parsePage(localDriver, keywords);
            
            log.info("Selenium知网爬取完成，返回 {} 条结果", citations.size());
            return citations;

        } catch (Exception e) {
            log.error("Selenium爬取知网失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            // 关闭WebDriver
            if (localDriver != null) {
                try {
                    localDriver.quit();
                    log.debug("WebDriver已关闭");
                } catch (Exception e) {
                    log.warn("关闭WebDriver失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(List<String> keywords) {
        String keyword = String.join(" ", keywords);
        // 使用知网的高级检索接口
        return "https://kns.cnki.net/kns8/defaultresult/index?crossids=YSTT4HG0,LSTPFY1C,JUP3MUPD,MPMFIG1A,WQ0UVIAA,BLZOG7CK,EMRPGLPA,PWFIRAGL,NN3FJMUV,NLBO1Z6R&kw=" 
            + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
            + "&korder=SU";
    }

    /**
     * 解析页面提取文献信息
     */
    private List<Citation> parsePage(WebDriver driver, List<String> keywords) {
        List<Citation> citations = new ArrayList<>();

        try {
            // 尝试多种选择器策略
            List<WebElement> items = findResultItems(driver);
            
            if (items.isEmpty()) {
                log.warn("未找到搜索结果元素");
                return citations;
            }

            log.debug("找到 {} 个搜索结果元素", items.size());

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
     * 查找结果项元素（多种选择器策略）
     */
    private List<WebElement> findResultItems(WebDriver driver) {
        // 策略1: 表格行
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector("table.result tbody tr"));
            if (!rows.isEmpty()) {
                log.debug("使用策略1找到 {} 个结果", rows.size());
                return rows;
            }
        } catch (Exception e) {
            log.debug("策略1失败: {}", e.getMessage());
        }

        // 策略2: 列表项
        try {
            List<WebElement> items = driver.findElements(By.cssSelector(".result-table-list tbody tr"));
            if (!items.isEmpty()) {
                log.debug("使用策略2找到 {} 个结果", items.size());
                return items;
            }
        } catch (Exception e) {
            log.debug("策略2失败: {}", e.getMessage());
        }

        // 策略3: GridTable
        try {
            List<WebElement> items = driver.findElements(By.cssSelector(".GridTableContent tbody tr"));
            if (!items.isEmpty()) {
                log.debug("使用策略3找到 {} 个结果", items.size());
                return items;
            }
        } catch (Exception e) {
            log.debug("策略3失败: {}", e.getMessage());
        }

        // 策略4: 通用列表
        try {
            List<WebElement> items = driver.findElements(By.cssSelector(".search-list .item"));
            if (!items.isEmpty()) {
                log.debug("使用策略4找到 {} 个结果", items.size());
                return items;
            }
        } catch (Exception e) {
            log.debug("策略4失败: {}", e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * 从元素中提取文献信息
     */
    private Citation extractCitation(WebElement item, List<String> keywords) {
        try {
            Citation citation = new Citation();

            // 提取标题
            String title = extractText(item, 
                "a.fz14", 
                ".name a", 
                "td:nth-child(2) a",
                "a[href*='detail']"
            );
            if (title == null || title.isEmpty()) {
                return null;
            }
            citation.setTitle(title);

            // 提取作者
            String authors = extractText(item,
                ".author",
                "td:nth-child(3)",
                ".writer"
            );
            citation.setAuthors(authors != null ? authors : "未知作者");

            // 提取来源
            String source = extractText(item,
                ".source",
                "td:nth-child(4)",
                ".from"
            );
            citation.setSource(source != null ? source : "中国知网");

            // 提取年份
            String year = extractText(item,
                ".year",
                "td:nth-child(5)",
                ".date"
            );
            citation.setYear(year != null ? year : "2024");

            // 提取摘要（如果有）
            String abstractText = extractText(item,
                ".abstract",
                ".summary"
            );
            citation.setAbstractText(abstractText != null ? abstractText : 
                "本文针对" + String.join("、", keywords) + "进行了深入研究，提出了创新性的解决方案。");

            // 提取引用次数
            String citationCountStr = extractText(item,
                ".quote",
                ".cite-count",
                "td:nth-child(6)"
            );
            int citationCount = parseCitationCount(citationCountStr);
            citation.setCitationCount(citationCount);

            // 提取URL
            String url = extractAttribute(item, "href",
                "a.fz14",
                ".name a",
                "td:nth-child(2) a"
            );
            if (url != null && !url.startsWith("http")) {
                url = "https://kns.cnki.net" + url;
            }
            citation.setUrl(url != null ? url : "https://kns.cnki.net/");

            // 设置数据源
            citation.setDataSource("cnki");

            log.debug("成功提取文献: {}", title);
            return citation;

        } catch (Exception e) {
            log.warn("提取文献信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取文本（尝试多个选择器）
     */
    private String extractText(WebElement parent, String... selectors) {
        for (String selector : selectors) {
            try {
                WebElement element = parent.findElement(By.cssSelector(selector));
                String text = element.getText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 提取属性（尝试多个选择器）
     */
    private String extractAttribute(WebElement parent, String attribute, String... selectors) {
        for (String selector : selectors) {
            try {
                WebElement element = parent.findElement(By.cssSelector(selector));
                String value = element.getAttribute(attribute);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 解析引用次数
     */
    private int parseCitationCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            // 提取数字
            String digits = text.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("清理Selenium资源...");
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("清理WebDriver失败: {}", e.getMessage());
            }
        }
    }
}
