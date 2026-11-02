package com.promptgateway.service;

/** 대화의 메시지 하나. role은 system, user, assistant 중 하나이다(FR-11). */
public record ChatTurn(String role, String text) {
}
