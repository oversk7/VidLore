package com.example.server.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * Executor 的原始输出：结论只携带证据编号，不携带原文。
 * 原文、时间戳与来源由 {@code EvidenceCatalog} 按编号回填，转换成 {@link AnalysisResult}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutorDraft(
        String title,
        List<Conclusion> conclusions,
        List<String> suggestions,
        List<AnalysisResult.Section> sections
) {
    public ExecutorDraft {
        conclusions = conclusions == null ? List.of() : conclusions.stream().filter(Objects::nonNull).toList();
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conclusion(String text, List<String> refs) {
        @JsonCreator
        public Conclusion {
            text = text == null ? "" : text.trim();
            refs = refs == null ? List.of() : refs.stream().filter(Objects::nonNull).toList();
        }

        /** 模型偶尔仍按旧格式返回纯字符串结论：保留结论，但它没有证据，会在 Critic 阶段被标记。 */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Conclusion of(String text) {
            return new Conclusion(text, List.of());
        }
    }
}
