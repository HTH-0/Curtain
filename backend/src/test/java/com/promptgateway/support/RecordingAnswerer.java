package com.promptgateway.support;

import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.ChatTurn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 시험용 가짜 응답 모델. 외부 AI로 간 대화를 호출마다 기록하고, 정해 둔 규칙으로 답을 만든다.
 * 대화 입구(FR-11)는 대화 메서드만 부르므로 문자열 메서드가 불리면 시험이 실패한다.
 */
public final class RecordingAnswerer implements AnswerGenerator {

    private final List<List<ChatTurn>> calls = new ArrayList<>();
    private final Function<List<ChatTurn>, String> reply;

    public RecordingAnswerer(Function<List<ChatTurn>, String> reply) {
        this.reply = reply;
    }

    /** 마지막 메시지를 "답: "과 함께 되돌려 준다(가명이 답에 들어가는 상황을 흉내 낸다). */
    public static RecordingAnswerer echoLast() {
        return new RecordingAnswerer(conversation -> "답: " + conversation.getLast().text());
    }

    @Override
    public String generateAnswer(String prompt) {
        throw new AssertionError("대화 입구는 대화 메서드를 불러야 한다");
    }

    @Override
    public String generateAnswer(List<ChatTurn> conversation) {
        calls.add(List.copyOf(conversation));
        return reply.apply(conversation);
    }

    /** 외부 AI가 받은 대화들(호출마다 하나). */
    public List<List<ChatTurn>> calls() {
        return calls;
    }

    /** 외부 AI가 받은 모든 글자를 이어 붙인 것(원래 값이 나갔는지 찾는 용도). */
    public String allSentText() {
        StringBuilder sb = new StringBuilder();
        calls.forEach(c -> c.forEach(t -> sb.append(t.role()).append(": ").append(t.text()).append('\n')));
        return sb.toString();
    }
}
