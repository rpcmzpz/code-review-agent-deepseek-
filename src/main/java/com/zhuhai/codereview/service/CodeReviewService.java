package com.zhuhai.codereview.service;
import org.springframework.stereotype.Service;
import com.zhuhai.codereview.model.CodeReviewRequest;
import com.zhuhai.codereview.model.CodeReviewResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuhai.codereview.model.Issue;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CodeReviewService {
  private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);
  private final DeepSeekClient deepSeekClient;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  private static final String JSON_FORMAT = """
      {
        "issues": [
          {
            "severity": "ERROR",
            "category": "维度名",
            "line": 行号,
            "title": "简短标题",
            "description": "问题描述",
            "suggestion": "修复建议"
          }
        ],
        "summary": "整体评价"
      }""";

  public CodeReviewService(DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
    this.deepSeekClient = deepSeekClient;
    this.objectMapper = objectMapper;
  }

  private String buildPrompt(String dimension) {
    Map<String, String> prompts = Map.of(
      "bug", "你是代码缺陷检测专家。只关注逻辑错误、空指针、边界条件、异常处理。忽略代码风格和性能问题。",
      "security", "你是代码安全审计专家。只关注注入攻击、敏感信息泄露、权限绕过。忽略代码风格和性能问题。",
      "performance", "你是代码性能优化专家。只关注不必要的循环、重复查询、内存浪费。忽略代码风格和安全问题。"
    );
    String rolePrompt = prompts.getOrDefault(dimension, prompts.get("bug"));
    return rolePrompt + "\n严格按照以下 JSON 格式返回（字段名必须完全一致，不要用 type/recommendation 等名字）：\n" + JSON_FORMAT;
  }

  // 单个维度的审查结果：issues + 维度 summary
  private record DimResult(List<Issue> issues, String summary) {}

  private DimResult reviewByDimension(String dimension, CodeReviewRequest request) {
      try {
        String systemPrompt = buildPrompt(dimension);
        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", "请审查以下代码：\n" + request.getCode() + "\n语言：" + request.getLanguage())
        );
        String responseJson = deepSeekClient.chat(messages);
        responseJson = responseJson.replaceAll("^```(json)?|```$", "").trim();
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode issuesNode = root.get("issues");
        List<Issue> issues = (issuesNode != null && issuesNode.isArray())
          ? objectMapper.readValue(issuesNode.traverse(), new TypeReference<List<Issue>>() {})
          : List.of();
        String summary = root.has("summary") ? root.get("summary").asText() : "";
        return new DimResult(issues, summary);
      } catch (Exception e) {
        log.error("维度 {} 审查失败", dimension, e);
        return new DimResult(List.of(), "");
      }
  }
  private CodeReviewResponse mergeResults(List<DimResult> results) {
      Set<String> seen = new HashSet<>();
      List<Issue> issues = results.stream()
        .map(DimResult::issues)
        .flatMap(List::stream)
        .filter(issue -> issue.getTitle() != null)
        .filter(issue -> seen.add(issue.getLine() + ":" + issue.getTitle()))
        .collect(Collectors.toList());

      String mergedSummary = results.stream()
        .map(DimResult::summary)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(" | "));

      CodeReviewResponse response = new CodeReviewResponse();
      response.setSuccess(true);
      response.setIssues(issues);
      response.setTotalIssues(issues.size());
      response.setSummary(mergedSummary.isEmpty() ? "审查完成" : mergedSummary);
      return response;
    }
  
  public CodeReviewResponse review(CodeReviewRequest request) {
    List<String> dims = request.getDimensions();
    if(dims == null || dims.isEmpty()) {
      dims = List.of("bug");
    }

    // 每个维度异步并行调用 DeepSeek API
    List<CompletableFuture<DimResult>> futures = dims.stream()
        .map(dim -> CompletableFuture.supplyAsync(
            () -> reviewByDimension(dim, request), executor
        ).exceptionally(ex -> {
            log.error("维度 {} 审查失败", dim, ex);
            return new DimResult(List.of(), "");
        }))
        .toList();

    // 等待全部完成，收集结果
    List<DimResult> results = futures.stream()
        .map(CompletableFuture::join)
        .toList();

    return mergeResults(results);
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
  }
}