package com.promptgateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 화면이 지금 모드를 보이려고 묻는 창구(AC-12.5). 서버 기본 응답이 시연인지 서버 키인지만 알리고 키 값은 싣지 않는다.
 */
@RestController
public class InfoController {

    private final boolean demo;

    public InfoController(@Value("${gateway.demo-answer:false}") boolean demo) {
        this.demo = demo;
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of("defaultMode", demo ? "demo" : "server-key");
    }
}
