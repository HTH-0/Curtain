package com.promptgateway.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 시연용 가짜 응답 모델(gateway.demo-answer=true). 외부 AI를 부르지 않는다. 키 없이 포털을 시연하기 위한 것이다.
 *
 *   포털 예시 문장   미리 써 둔 모범 답을 돌려준다(AC-12.8). 답에는 가명이 들어 있어 게이트웨이가 실제로 원래 값으로 복원한다.
 *   그 밖의 문장     받은 문장(외부 AI가 받았을 가린 문장)을 그대로 되돌려 주고, 실제 답은 내 키로 받으라고 안내한다.
 *
 * 받는 문장은 이미 가려진 문장이므로 모범 답은 가린 문장을 열쇠로 찾는다. 화면(index.html)의 예시가 바뀌면 여기도 바꾼다(DemoExampleAnswersTest).
 */
@Service
@ConditionalOnProperty(name = "gateway.demo-answer", havingValue = "true")
public class DemoAnswerGenerator implements AnswerGenerator {

    private static final String CANNED = "(미리 써 둔 시연용 예시 답변입니다. 실제 AI의 답은 \"내 키로 써 보기\"에서 받을 수 있습니다)\n\n";

    /** 화면의 "경고: 대량 명단" 예시를 가린 문장(직원1~22의 번호가 [전화1]~[전화22]가 된다). */
    private static final String BULK_MASKED = IntStream.rangeClosed(1, 22).mapToObj(i -> "직원" + i + " [전화" + i + "]")
            .collect(Collectors.joining("\n")) + "\n위 명단 부서별로 정리해 줘";

    private static final Map<String, String> EXAMPLES = Map.of(
            "아래 고객 연락처를 표로 정리해 줘\n김민수 [전화1] [이메일1]\n박서연 [전화2] [이메일2]",
            """
            고객 연락처를 표로 정리했습니다.

            | 이름 | 연락처 | 이메일 |
            |---|---|---|
            | 김민수 | [전화1] | [이메일1] |
            | 박서연 | [전화2] | [이메일2] |""",

            "[고객사1] 미팅에서 [코드명1] 출시 일정 공유한 내용으로 감사 메일 초안 써 줘",
            """
            제목: [고객사1] 미팅 감사 인사

            안녕하세요, [고객사1] 담당자님.
            오늘 미팅에서 [코드명1] 출시 일정을 함께 살펴봐 주셔서 감사합니다.
            공유드린 일정에 맞춰 준비 상황을 정리해 다시 안내드리겠습니다.

            감사합니다.""",

            BULK_MASKED,
            """
            명단에 부서 정보가 없어 부서별로 나눌 수 없습니다. 부서를 함께 알려 주시면 정리해 드리겠습니다.
            우선 받은 순서대로 정리했습니다.

            - 직원1: [전화1]
            - 직원2: [전화2]
            - 직원3: [전화3]
            - ... (직원4~직원21 생략)
            - 직원22: [전화22]""",

            "주간 회의록을 세 줄로 요약하는 방법 알려 줘",
            """
            세 줄에 아래를 하나씩 담으면 됩니다.

            1. 결정된 사항
            2. 다음 할 일과 담당자, 기한
            3. 남은 쟁점이나 다음 회의 일정

            세 줄 모두 "누가, 무엇을, 언제"가 드러나게 쓰면 읽는 사람이 바로 이해합니다.""",

            "주민등록번호는 몇 자리야?",
            "주민등록번호는 13자리입니다. 앞 6자리는 생년월일, 뒤 7자리는 성별과 고유 번호입니다.");

    @Override
    public String generateAnswer(String prompt) {
        String example = EXAMPLES.get(prompt);
        if (example != null) {
            return CANNED + example;
        }
        return "(시연용 답변: 외부 AI를 부르지 않았습니다) 받은 요청은 다음과 같습니다.\n" + prompt
                + "\n\n실제 AI의 답은 \"내 키로 써 보기\"에 Gemini 키를 넣으면 받을 수 있습니다.";
    }

    /** 대화에서는 마지막 사용자 메시지에 같은 틀로 답한다. */
    @Override
    public String generateAnswer(List<ChatTurn> conversation) {
        String lastUser = conversation.reversed().stream().filter(t -> "user".equals(t.role())).map(ChatTurn::text).findFirst()
                .orElse(conversation.getLast().text());
        return generateAnswer(lastUser);
    }
}
