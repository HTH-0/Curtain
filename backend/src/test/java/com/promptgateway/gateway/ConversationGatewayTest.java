package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.detect.Item;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.ChatTurn;
import com.promptgateway.support.RecordingAnswerer;
import com.promptgateway.support.TestConversations;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.promptgateway.support.TestConversations.assistant;
import static com.promptgateway.support.TestConversations.system;
import static com.promptgateway.support.TestConversations.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 호환 입구의 처리 흐름(FR-11, design.md 9장). 가짜 응답 모델이 받은 대화를 기록해서 무엇이 밖으로 나갔는지 직접 확인한다.
 * 값은 모두 가짜이다.
 */
class ConversationGatewayTest {

    RecordingAnswerer answerer;
    List<String> logLines;
    ConversationGateway gateway;

    @BeforeEach
    void setUp() {
        answerer = RecordingAnswerer.echoLast();
        logLines = new ArrayList<>();
        gateway = TestConversations.gateway(answerer, new GatewayLogger(logLines::add));
    }

    @Test
    @DisplayName("AC-11.2 탐지가 없으면 대화를 그대로 외부 AI에 보내고 답을 돌려준다(안내 없음)")
    void allow() {
        List<ChatTurn> conversation = List.of(system("한국어로 답해"), user("회의록 요약해 줘"));

        ConversationResult r = gateway.handle(conversation);

        assertThat(r.action()).isEqualTo(Action.ALLOW);
        assertThat(answerer.calls()).containsExactly(conversation);
        assertThat(r.answer()).isEqualTo("답: 회의록 요약해 줘");
        assertThat(r.counts()).isEmpty();
    }

    @Test
    @DisplayName("AC-11.3, 11.4 모든 메시지의 값을 한 대응표로 가린다: 외부 AI에는 원래 값이 하나도 가지 않고 역할은 그대로이다")
    void maskReplacesValuesInEveryMessage() {
        List<ChatTurn> conversation = List.of(
                system("담당자 연락처는 010-8765-4321"),
                user("김민수 010-2345-6789 에게 메일 써 줘"),
                assistant("알겠습니다 010-2345-6789"),
                user("한결물산 건도 정리해 줘"));

        ConversationResult r = gateway.handle(conversation);

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(answerer.calls()).hasSize(1);
        List<ChatTurn> sent = answerer.calls().getFirst();
        assertThat(sent).extracting(ChatTurn::role).containsExactly("system", "user", "assistant", "user");
        assertThat(sent).extracting(ChatTurn::text).containsExactly(
                "담당자 연락처는 [전화1]",
                "김민수 [전화2] 에게 메일 써 줘",
                "알겠습니다 [전화2]",
                "[고객사1] 건도 정리해 줘");
        assertThat(answerer.allSentText()).doesNotContain("010-8765-4321", "010-2345-6789", "한결물산");
    }

    @Test
    @DisplayName("AC-11.3 값이 사용자 메시지가 아니라 이전 답(assistant)에만 있어도 가린다")
    void detectsInEveryRole() {
        ConversationResult r = gateway.handle(List.of(user("연락처 알려 줘"), assistant("김민수 010-2345-6789 입니다"), user("고마워")));

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(answerer.allSentText()).doesNotContain("010-2345-6789").contains("[전화1]");
    }

    @Test
    @DisplayName("AC-11.6 답의 가명을 원래 값으로 되돌리고, 맨 앞에 가린 범주와 개수를 알리는 한 줄을 붙인다")
    void restoresAnswerAndAddsNotice() {
        ConversationResult r = gateway.handle(List.of(user("김민수 010-2345-6789, 010-9876-5432 로 안내 메일 써 줘")));

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(r.answer()).isEqualTo(
                "[Deskguard] 휴대전화 2건을 가려서 보냈습니다.\n\n답: 김민수 010-2345-6789, 010-9876-5432 로 안내 메일 써 줘");
        assertThat(r.restored()).isEqualTo(2);
        assertThat(r.counts()).extracting(FindingCount::item, FindingCount::count).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Item.PHONE, 2));
    }

    @Test
    @DisplayName("AC-11.6 클라이언트가 이전 대화를 매번 되보내므로 같은 값은 한 번만 센다(턴이 늘어도 안내와 로그의 개수가 부풀지 않는다)")
    void countsEachDistinctValueOnce() {
        ConversationResult r = gateway.handle(List.of(
                user("김민수 010-2345-6789 에게 메일 써 줘"),
                assistant("[Deskguard] 휴대전화 1건을 가려서 보냈습니다.\n\n답: 김민수 010-2345-6789 에게 보낼 메일입니다"),
                user("010-2345-6789 말고 010-9876-5432 로도 보내 줘")));

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(r.counts()).extracting(FindingCount::item, FindingCount::count).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Item.PHONE, 2));
        assertThat(r.answer()).startsWith("[Deskguard] 휴대전화 2건을 가려서 보냈습니다.\n\n");
        assertThat(logLines.getFirst()).contains("findings=D1.PHONE:2");
    }

    @Test
    @DisplayName("AC-11.6 경고(대량)는 확인할 방법이 없으므로 확인 없이 가림으로 보낸다. 원문은 나가지 않는다")
    void warnIsMaskedWithoutConfirmation() {
        ConversationResult r = gateway.handle(List.of(user(TestGateways.phoneList(22))));

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(answerer.calls()).hasSize(1);
        assertThat(answerer.allSentText()).doesNotContain("010-2000-3000", "010-2021-3021");
        assertThat(r.answer()).startsWith("[Deskguard] 휴대전화 22건을 가려서 보냈습니다.");
        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).contains("action=MASK", "confirmed=true");
    }

    @Test
    @DisplayName("AC-11.3, 11.7 자격증명이 하나라도 있으면 차단이고 외부 AI를 부르지 않는다")
    void block() {
        ConversationResult r = gateway.handle(List.of(user("이 설정 왜 안 돼? DB_PASSWORD=Qw7!zLp92x"), assistant("연락처 010-2345-6789")));

        assertThat(r.action()).isEqualTo(Action.BLOCK);
        assertThat(r.answer()).isNull();
        assertThat(answerer.calls()).isEmpty();
        assertThat(r.counts()).extracting(FindingCount::item).contains(Item.PASSWORD, Item.PHONE);
        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).contains("action=BLOCK");
    }

    @Test
    @DisplayName("AC-11.5 클라이언트가 이전 답(복원된 값과 안내 줄 포함)을 되보내 대화를 늘려도 앞부분의 가명이 같다")
    void laterTurnKeepsSamePseudonyms() {
        ConversationResult first = gateway.handle(List.of(user("김민수 010-2345-6789 정리해 줘")));
        List<ChatTurn> second = List.of(
                user("김민수 010-2345-6789 정리해 줘"),
                assistant(first.answer()),
                user("박서연 010-9876-5432 도 추가해 줘"));

        gateway.handle(second);

        List<ChatTurn> firstSent = answerer.calls().get(0);
        List<ChatTurn> secondSent = answerer.calls().get(1);
        assertThat(secondSent.get(0)).isEqualTo(firstSent.get(0));
        assertThat(secondSent.get(1).text()).contains("[전화1]").doesNotContain("010-2345-6789");
        assertThat(secondSent.get(2).text()).isEqualTo("박서연 [전화2] 도 추가해 줘");
    }

    @Test
    @DisplayName("AC-11.14 이전 답의 맨 앞에 우리가 붙인 안내 줄은 외부 AI로 보내지 않는다(모델이 흉내 내 안내가 두 번 나오는 것을 막는다). 사용자 메시지는 그대로 둔다")
    void stripsOwnNoticeFromEarlierAnswersOnly() {
        ConversationResult first = gateway.handle(List.of(user("김민수 010-2345-6789 정리해 줘")));
        String notice = "[Deskguard] 휴대전화 1건을 가려서 보냈습니다.\n\n";
        assertThat(first.answer()).startsWith(notice);

        gateway.handle(List.of(
                user("김민수 010-2345-6789 정리해 줘"),
                assistant(first.answer()),
                user(notice + "이 문장은 사용자가 직접 쓴 것이다")));

        List<ChatTurn> sent = answerer.calls().get(1);
        assertThat(sent.get(1).text()).isEqualTo("답: 김민수 [전화1] 정리해 줘");
        assertThat(sent.get(2).text()).isEqualTo(notice + "이 문장은 사용자가 직접 쓴 것이다");
    }

    @Test
    @DisplayName("AC-11.8 잘못된 대화(없음, 비어 있음, 허용 밖 역할, 모두 공백, 길이 초과)는 외부 AI를 부르지 않고 거부하며 로그 한 줄을 남긴다")
    void rejectsInvalidConversations() {
        ConversationGateway small = TestConversations.gateway(answerer, new GatewayLogger(logLines::add), 50);
        List<List<ChatTurn>> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(List.of());
        invalid.add(List.of(new ChatTurn("tool", "안녕")));
        invalid.add(List.of(new ChatTurn(null, "안녕")));
        invalid.add(List.of(user("   "), assistant("")));
        invalid.add(List.of(user("가".repeat(30)), assistant("나".repeat(30))));

        for (List<ChatTurn> conversation : invalid) {
            logLines.clear();
            assertThatThrownBy(() -> small.handle(conversation)).isInstanceOf(InvalidPromptException.class);
            assertThat(logLines).hasSize(1);
            assertThat(logLines.getFirst()).contains("action=ERROR", "stage=VALIDATION");
        }
        assertThat(answerer.calls()).isEmpty();
    }

    @Test
    @DisplayName("AC-11.8 메시지 하나가 공백이어도 다른 메시지에 내용이 있으면 받아들인다")
    void acceptsOneBlankMessage() {
        ConversationResult r = gateway.handle(List.of(user("안녕"), assistant("")));

        assertThat(r.action()).isEqualTo(Action.ALLOW);
        assertThat(answerer.calls()).hasSize(1);
    }

    @Test
    @DisplayName("AC-11.11 요청당 로그는 정확히 한 줄이고 원문과 탐지한 값이 없으며 길이와 해시가 있다")
    void logsOneLineWithoutValues() {
        gateway.handle(List.of(user("김민수 010-2345-6789 와 minsu.k@mail.example 정리"), assistant("네")));

        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).contains("action=MASK", "findings=D1.PHONE:1,D1.EMAIL:1", "promptLength=", "promptHash=")
                .doesNotContain("010-2345-6789", "minsu.k@mail.example", "김민수");
    }

    @Test
    @DisplayName("AC-11.10 외부 AI 오류는 그대로 전달하고, 로그에는 예외 종류만 남긴다(메시지의 키는 남기지 않는다)")
    void upstreamErrorIsLoggedWithoutMessage() {
        RecordingAnswerer failing = new RecordingAnswerer(c -> {
            throw new IllegalStateException("boom https://x.example/v1?key=SECRET-KEY");
        });
        ConversationGateway failingGateway = TestConversations.gateway(failing, new GatewayLogger(logLines::add));

        assertThatThrownBy(() -> failingGateway.handle(List.of(user("회의록 요약해 줘")))).isInstanceOf(IllegalStateException.class);

        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).contains("action=ERROR", "stage=ANSWER", "errorKind=IllegalStateException")
                .doesNotContain("SECRET-KEY");
    }

    @Test
    @DisplayName("AC-11.3 한 문장짜리 대화의 탐지와 처리는 /api/chat과 같다(경고만 확인 없이 가림으로 바뀐다)")
    void sameDetectionAsChatEndpoint() {
        ChatGateway chat = TestGateways.gateway(p -> "x", new GatewayLogger(l -> { }), false);
        List<String> sentences = List.of(
                "회의록 요약해 줘",
                "주민등록번호는 몇 자리야?",
                "김민수 010-2345-6789, 한결물산 담당자 표로 정리",
                "이 설정 왜 안 돼? DB_PASSWORD=Qw7!zLp92x, 담당 010-2345-6789",
                "[대외비] 사업계획 요약해 줘",
                TestGateways.phoneList(20));

        for (String sentence : sentences) {
            GatewayResult expected = chat.handle(sentence, false, false);
            ConversationResult actual = gateway.handle(List.of(user(sentence)));

            Action expectedAction = expected.action() == Action.WARN ? Action.MASK : expected.action();
            assertThat(actual.action()).as(sentence).isEqualTo(expectedAction);
            assertThat(actual.counts()).as(sentence).isEqualTo(expected.counts());
        }
    }
}
