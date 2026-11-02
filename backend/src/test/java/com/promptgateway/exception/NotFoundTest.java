package com.promptgateway.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 없는 주소는 404이다(AC-12.9). 공개 배포에서 없는 그림 파일이 500(서버 오류)으로 응답하는 것을 발견해 추가했다. */
@SpringBootTest(properties = "gateway.demo-answer=true")
class NotFoundTest {

    @Autowired WebApplicationContext context;

    @Test
    @DisplayName("AC-12.9 없는 파일과 없는 API 주소는 404이고 서버 오류 문구를 싣지 않는다. 있는 파일은 200이다")
    void missingPathIs404() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        for (String path : new String[] {"/img/no-such-file.svg", "/api/no-such-thing"}) {
            MockHttpServletResponse r = mvc.perform(get(path)).andReturn().getResponse();
            assertThat(r.getStatus()).as(path).isEqualTo(404);
            assertThat(r.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("INTERNAL_ERROR");
        }
        assertThat(mvc.perform(get("/img/curtain.svg")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
