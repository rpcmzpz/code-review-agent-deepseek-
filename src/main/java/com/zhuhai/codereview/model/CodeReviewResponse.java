package com.zhuhai.codereview.model;

import java.util.List;
import lombok.Data;
@Data
public class CodeReviewResponse {
    private boolean success;
    private List<Issue> issues;
    private String summary;
    private int totalIssues;
}
