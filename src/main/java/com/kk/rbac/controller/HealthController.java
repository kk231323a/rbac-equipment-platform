package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    //    @GetMapping
//    public Map<String, String> health() {
//        return Map.of("status", "ok");
//    }
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(
                Map.of("status", "ok")
        );
    }
}
