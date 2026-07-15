package com.zhuhai.codereview.model;
import java.util.List;
import lombok.Data;

@Data
public class CodeReviewRequest {
    private String code;
    private String language;
    private List<String> dimensions;
    private String context;
}
