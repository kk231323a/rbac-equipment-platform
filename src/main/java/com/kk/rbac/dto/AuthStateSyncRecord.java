package com.kk.rbac.dto;

import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;


public record AuthStateSyncRecord(Integer userId,Long targetTokenVersion) {
}
