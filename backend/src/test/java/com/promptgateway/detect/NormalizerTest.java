package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 탐지용 정규화 사본과 원문 위치 되돌림(AC-13.1, AC-13.2). */
class NormalizerTest {

    private static final String ZW = "​";

    @Test
    @DisplayName("AC-13.1 변형이 없는 문장은 그대로이고 위치도 같다")
    void plainTextIsUnchanged() {
        String text = "평범한 문장 010-1234-5678 입니다";

        Normalizer.Result r = Normalizer.normalize(text);

        assertThat(r.text()).isEqualTo(text);
        assertThat(r.toOriginalStart(7)).isEqualTo(7);
        assertThat(r.toOriginalEnd(text.length())).isEqualTo(text.length());
    }

    @Test
    @DisplayName("AC-13.1 ① 전각 숫자와 기호를 반각으로 바꾼다(글자 수는 같다)")
    void fullwidthToHalfwidth() {
        Normalizer.Result r = Normalizer.normalize("연락처 ０１０－１２３４－５６７８");

        assertThat(r.text()).isEqualTo("연락처 010-1234-5678");
        assertThat(r.toOriginalStart(4)).isEqualTo(4);
        assertThat(r.toOriginalEnd(r.text().length())).isEqualTo(r.text().length());
    }

    @Test
    @DisplayName("AC-13.1 ① 한 글자가 여러 글자로 풀리면 모두 원문의 그 한 글자로 되돌린다")
    void expandedCharacterMapsToItsOriginal() {
        Normalizer.Result r = Normalizer.normalize("a㈜b");

        assertThat(r.text()).isEqualTo("a(주)b");
        assertThat(r.toOriginalStart(1)).isEqualTo(1);
        assertThat(r.toOriginalEnd(4)).isEqualTo(2);
        assertThat(r.toOriginalStart(4)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-13.1 ② 보이지 않는 문자를 지우고, 값 사이에 낀 것은 원문 구간에 들어간다")
    void zeroWidthIsRemoved() {
        Normalizer.Result r = Normalizer.normalize("01" + ZW + "0-1234-5678");

        assertThat(r.text()).isEqualTo("010-1234-5678");
        assertThat(r.toOriginalStart(0)).isEqualTo(0);
        assertThat(r.toOriginalStart(2)).isEqualTo(3);
        assertThat(r.toOriginalEnd(r.text().length())).isEqualTo(r.text().length() + 1);
    }

    @Test
    @DisplayName("AC-13.2 값 끝에 붙은 보이지 않는 문자는 구간에 넣지 않는다")
    void trailingInvisibleIsOutsideTheSpan() {
        Normalizer.Result r = Normalizer.normalize("010-1234-5678" + ZW);

        assertThat(r.text()).isEqualTo("010-1234-5678");
        assertThat(r.toOriginalEnd(13)).isEqualTo(13);
    }

    @Test
    @DisplayName("AC-13.1 ③ 한글 숫자가 3글자 이상 이어지면 아라비아 숫자로 바꾼다")
    void koreanDigitRunsBecomeDigits() {
        Normalizer.Result r = Normalizer.normalize("연락처 공일공 일이삼사 오육칠팔");

        assertThat(r.text()).isEqualTo("연락처 010 1234 5678");
        assertThat(r.text().length()).isEqualTo("연락처 공일공 일이삼사 오육칠팔".length());
    }

    @Test
    @DisplayName("AC-13.1 ③ 한글 숫자 글자가 일반 낱말 안에 있거나 3글자 미만이면 바꾸지 않는다")
    void shortKoreanDigitRunsAreKept() {
        assertThat(Normalizer.normalize("오일 사이 일이 삼성전자").text()).isEqualTo("오일 사이 일이 삼성전자");
    }

    @Test
    @DisplayName("AC-13.1 ② 다음 ③ 보이지 않는 문자를 먼저 지운 뒤 한글 숫자 길이를 센다")
    void invisibleRemovedBeforeKoreanDigitRun() {
        Normalizer.Result r = Normalizer.normalize("공" + ZW + "일공");

        assertThat(r.text()).isEqualTo("010");
        assertThat(r.toOriginalEnd(3)).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-13.1 ④ 한 글자씩 공백으로 떨어진 숫자와 하이픈 7개 이상은 공백을 지운다")
    void spacedDigitsAreJoined() {
        String spaced = "0 1 0 - 1 2 3 4 - 5 6 7 8";

        Normalizer.Result r = Normalizer.normalize("번호 " + spaced + " 입니다");

        assertThat(r.text()).isEqualTo("번호 010-1234-5678 입니다");
        assertThat(r.toOriginalStart(3)).isEqualTo(3);
        assertThat(r.toOriginalEnd(16)).isEqualTo(3 + spaced.length());
    }

    @Test
    @DisplayName("AC-13.1 ④ 6개 이하이거나 여러 자리 숫자 덩어리는 건드리지 않는다")
    void shortOrMultiDigitTokensAreKept() {
        assertThat(Normalizer.normalize("1 2 3 4 5 6").text()).isEqualTo("1 2 3 4 5 6");
        assertThat(Normalizer.normalize("2024 10 12 15 30 45 59").text()).isEqualTo("2024 10 12 15 30 45 59");
        assertThat(Normalizer.normalize("1 2 3 4 5 6 7").text()).isEqualTo("1234567");
    }

    @Test
    @DisplayName("AC-13.1 대리쌍 글자(이모지)가 있어도 글자 수와 위치가 어긋나지 않는다")
    void surrogatePairsKeepPositions() {
        String text = "😀 ０１０-1234-5678";

        Normalizer.Result r = Normalizer.normalize(text);

        assertThat(r.text()).isEqualTo("😀 010-1234-5678");
        assertThat(r.toOriginalStart(3)).isEqualTo(3);
        assertThat(r.toOriginalEnd(r.text().length())).isEqualTo(text.length());
    }
}
