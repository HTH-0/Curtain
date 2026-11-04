package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 탐지기 조합, 허용 목록, 겹침 정리(AC-2.5, design.md 2장). */
class DetectionEngineTest {

    final DetectionEngine engine = new DetectionEngine(
            List.of(new PersonalDataDetector(), new SecretDetector(),
                    new DictionaryDetector(List.of("블루웨일"), List.of("한결물산"), List.of("대외비"))),
            Set.of("contact@hth.example"));

    @Test
    @DisplayName("여러 범주를 한 번에 찾고 위치 순서로 돌려준다")
    void combinesDetectors() {
        List<Finding> found = engine.detect("블루웨일 담당 010-2345-6789, password=Qw7!zLp92x");

        assertThat(found).extracting(Finding::item).containsExactly(Item.CODENAME, Item.PHONE, Item.PASSWORD);
    }

    @Test
    @DisplayName("AC-2.5 허용 목록의 값은 빼다")
    void allowlist() {
        assertThat(engine.detect("문의는 contact@hth.example, 담당 kim@mail.example")).extracting(Finding::value)
                .containsExactly("kim@mail.example");
    }

    @Test
    @DisplayName("겹친 탐지는 더 긴 것 하나만 남긴다(접속 문자열 안의 이메일 모양을 따로 세지 않는다)")
    void overlapsKeepLongest() {
        List<Finding> found = engine.detect("jdbc mysql://admin:Gx82!kqP@db.hth.example:3306/app");

        assertThat(found).extracting(Finding::item).containsExactly(Item.CONN_STRING);
    }

    @Test
    @DisplayName("AC-13.1, 13.2 전각 숫자 전화를 찾고 위치와 값은 원문 기준이다")
    void findsFullwidthPhoneWithOriginalSpan() {
        String text = "연락처 ０１０－２３４５－６７８９ 입니다";

        List<Finding> found = engine.detect(text);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).item()).isEqualTo(Item.PHONE);
        assertThat(found.get(0).value()).isEqualTo("０１０－２３４５－６７８９");
        assertThat(text.substring(found.get(0).start(), found.get(0).end())).isEqualTo(found.get(0).value());
    }

    @Test
    @DisplayName("AC-13.1 한글 숫자로 쓴 전화를 찾는다")
    void findsKoreanDigitPhone() {
        List<Finding> found = engine.detect("연락처 공일공 이삼사오 육칠팔구 입니다");

        assertThat(found).extracting(Finding::item).containsExactly(Item.PHONE);
        assertThat(found.get(0).value()).isEqualTo("공일공 이삼사오 육칠팔구");
    }

    @Test
    @DisplayName("AC-13.2 값 사이에 보이지 않는 문자가 끼어 있어도 찾고, 가림하면 원형이 남지 않는다")
    void findsPhoneWithZeroWidthAndMasksIt() {
        String text = "연락처 010-23​45-6789 입니다";

        List<Finding> found = engine.detect(text);
        String masked = new com.promptgateway.mask.Masker().mask(text, found).maskedText();

        assertThat(found).extracting(Finding::item).containsExactly(Item.PHONE);
        assertThat(masked).isEqualTo("연락처 [전화1] 입니다");
        assertThat(masked).doesNotContain("​");
    }

    @Test
    @DisplayName("AC-13.1 한 글자씩 띄어 쓴 전화를 찾는다")
    void findsSpacedDigitPhone() {
        String text = "번호 0 1 0 - 2 3 4 5 - 6 7 8 9 입니다";

        List<Finding> found = engine.detect(text);

        assertThat(found).extracting(Finding::item).containsExactly(Item.PHONE);
        assertThat(found.get(0).value()).isEqualTo("0 1 0 - 2 3 4 5 - 6 7 8 9");
    }

    @Test
    @DisplayName("AC-13.3 변형 없는 값은 이전처럼 한 번만 찾는다")
    void plainValueIsFoundOnce() {
        assertThat(engine.detect("연락처 010-2345-6789")).hasSize(1);
    }

    @Test
    @DisplayName("AC-13.3 허용 목록은 변형된 같은 값에도 적용한다")
    void allowlistAppliesToVariants() {
        assertThat(engine.detect("문의는 contact＠hth.example 로 하세요")).isEmpty();
    }

    @Test
    @DisplayName("AC-13.3 숫자 덩어리나 일반 낱말은 정규화로 새로 걸리지 않는다")
    void ordinaryTextIsNotFlagged() {
        assertThat(engine.detect("2024 10 12 15 30 45 59 일정과 오일 사이 일이 삼성전자 1 2 3 4 5 6")).isEmpty();
    }

    @Test
    @DisplayName("AC-2.8 탐지는 외부 호출 없이 끝난다(탐지기는 순수 코드다)")
    void noExternalCalls() {
        assertThat(engine.detect("평범한 회의록 요약해 줘")).isEmpty();
    }
}
