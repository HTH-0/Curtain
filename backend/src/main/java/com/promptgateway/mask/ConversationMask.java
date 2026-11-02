package com.promptgateway.mask;

import java.util.List;
import java.util.Map;

/** 대화의 메시지별로 가린 문장과, 대화 전체가 함께 쓰는 가명 대응표. 대응표는 요청이 끝나면 버린다(AC-4.4, AC-11.4). */
public record ConversationMask(List<String> maskedTexts, Map<String, String> pseudonymToValue) {
}
