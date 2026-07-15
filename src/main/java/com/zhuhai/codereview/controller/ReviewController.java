package com.zhuhai.codereview.controller;

import org.springframework.web.bind.annotation.RestController;
import com.zhuhai.codereview.model.CodeReviewRequest;
import com.zhuhai.codereview.model.CodeReviewResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.zhuhai.codereview.service.CodeReviewService;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final CodeReviewService codeReviewService;
    public ReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }
    @PostMapping("/review")
    public CodeReviewResponse submitReview(@RequestBody CodeReviewRequest request) {
      if (request.getCode() == null || request.getCode().isBlank()) {
        CodeReviewResponse error = new CodeReviewResponse();
        error.setSuccess(false);
        error.setSummary("代码不能为空");
        return error;
      }
      return codeReviewService.review(request);
  }
}

