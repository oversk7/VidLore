package com.example.server.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisResultTest {

    @Test
    void markdownListsEachEvidenceOnceInTimeOrder() {
        AnalysisResult result = new AnalysisResult("t", List.of("结论一", "结论二"), List.of(
                new AnalysisResult.Evidence(120_000, "OCR", "RAG 标题", "结论二"),
                new AnalysisResult.Evidence(19_000, "ASR", "LoRA 降低了成本。", "结论一"),
                new AnalysisResult.Evidence(19_000, "ASR", "LoRA 降低了成本。", "结论二"),
                new AnalysisResult.Evidence(112_000, "ASR", "RAG 和微调是两回事。", "结论二")),
                List.of(), null);

        String markdown = result.toMarkdown();

        assertTrue(markdown.contains("""
                ## 视频证据
                - [00:19] ASR：LoRA 降低了成本。
                - [01:52] ASR：RAG 和微调是两回事。
                - [02:00] OCR：RAG 标题
                """));
    }
}
