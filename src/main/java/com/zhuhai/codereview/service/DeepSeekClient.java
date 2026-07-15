package com.zhuhai.codereview.service;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class DeepSeekClient {
  @Value("${deepseek.api-key}")
  private String apiKey;
  @Value("${deepseek.base-url}")
  private String baseUrl;
  @Value("${deepseek.model}")
  private String model;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public DeepSeekClient(ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(30000);
    this.restTemplate = new RestTemplate(factory);
    this.objectMapper = objectMapper;
  }

  public String chat(List<Map<String, String>> messages) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    headers.set("Authorization", "Bearer " + apiKey);
    HashMap<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", messages);
    requestBody.put("stream", false);
    HttpEntity<HashMap<String, Object>> entity = new HttpEntity<>(requestBody, headers);
    String response = restTemplate.postForEntity(baseUrl + "/chat/completions", entity, String.class).getBody();
    try {
      JsonNode root = objectMapper.readTree(response);
      return root.get("choices").get(0).get("message").get("content").asText();
    } catch (Exception e) {
      return "{\"issues\":[], \"summary\":\"API 解析失败：" + e.getMessage() + "\"}";
    }
  }
}
