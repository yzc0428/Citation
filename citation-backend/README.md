# Citation Backend

智能文献引用搜索系统 - 后端服务

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring WebFlux (响应式编程)
- Maven

## 快速开始

### 前置要求

- JDK 17 或更高版本
- Maven 3.6+

### 启动服务

```bash
# 进入后端目录
cd citation-backend

# 启动服务
mvn spring-boot:run
```

服务将在 http://localhost:8080 启动

### API 端点

- `GET /api/health` - 健康检查
- `POST /api/search` - 搜索文献

### 配置

配置文件位于 `src/main/resources/application.yml`

可以通过环境变量配置：
- `AI_API_KEY` - AI 服务 API Key
- `GOOGLE_SCHOLAR_API_KEY` - 谷歌学术 API Key
- `CNKI_API_KEY` - 知网 API Key

## 项目结构

```
src/main/java/com/citation/
├── CitationApplication.java       # 主应用类
├── config/                        # 配置类
│   └── WebConfig.java            # Web 配置（CORS）
├── controller/                    # 控制器
│   ├── HealthController.java     # 健康检查
│   └── SearchController.java     # 搜索接口
├── service/                       # 服务层
│   ├── AIService.java            # AI 服务
│   ├── SearchService.java        # 搜索服务
│   ├── GoogleScholarService.java # 谷歌学术服务
│   └── CNKIService.java          # 知网服务
├── dto/                          # 数据传输对象
│   ├── SearchRequest.java        # 搜索请求
│   ├── SearchResponse.java       # 搜索响应
│   └── Citation.java             # 文献信息
└── exception/                     # 异常处理
    ├── BusinessException.java     # 业务异常
    └── GlobalExceptionHandler.java # 全局异常处理器
```
