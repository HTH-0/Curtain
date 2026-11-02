package com.promptgateway.service;

/**
 * 방문자가 넣은 Gemini 키를 Gemini가 거부했다(AC-12.4). 키 값과 외부 응답 본문은 담지 않는다.
 */
public class UserKeyRejectedException extends RuntimeException {

    public UserKeyRejectedException() {
        super("입력한 Gemini 키를 확인하세요. 키가 틀렸거나 사용할 수 없는 키입니다.");
    }
}
