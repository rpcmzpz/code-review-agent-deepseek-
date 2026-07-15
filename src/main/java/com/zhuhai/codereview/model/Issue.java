package com.zhuhai.codereview.model;
import lombok.Data;
@Data
public class Issue {
    private String severity;
    private String category;
    private int line;
    private String title;
    private String description;
    private String suggestion;
}
