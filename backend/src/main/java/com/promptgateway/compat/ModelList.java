package com.promptgateway.compat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** GET /v1/models 응답(OpenAI 목록 형식). */
public record ModelList(String object, List<Model> data) {

    public record Model(String id, String object, long created, @JsonProperty("owned_by") String ownedBy) {
    }
}
