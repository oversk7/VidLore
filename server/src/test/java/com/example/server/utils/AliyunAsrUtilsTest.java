package com.example.server.utils;

import com.example.server.dto.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunAsrUtilsTest {

    @Test
    void parsesSentenceTimestampsFromVerboseJson() {
        // 硅基流动 verbose_json 的真实返回格式（2026-10-20 实测，文字有删节）。
        String body = """
                {"duration":20.088,"text":"这个就涉及到微调的概念。当然，这个展开来也可以讲很长。",
                 "segments":[
                   {"type":"transcript.text.segment","id":"0","start":0.0,"end":12.44,"text":"这个就涉及到微调的概念。"},
                   {"type":"transcript.text.segment","id":"1","start":12.36,"end":20.08,"text":"当然，这个展开来也可以讲很长。"},
                   {"type":"transcript.text.segment","id":"2","start":20.08,"end":20.08,"text":"  "}
                 ]}
                """;

        assertThat(AliyunAsrUtils.parseSentences(body)).containsExactly(
                new TranscriptSegment(0, 12_440, "这个就涉及到微调的概念。"),
                new TranscriptSegment(12_360, 20_080, "当然，这个展开来也可以讲很长。"));
    }

    @Test
    void fallsBackToWholeTextWhenSegmentsAreMissing() {
        assertThat(AliyunAsrUtils.parseSentences("{\"duration\":20.088,\"text\":\"整段文字。\"}"))
                .containsExactly(new TranscriptSegment(0, 20_088, "整段文字。"));
    }

    @Test
    void returnsEmptyForBlankTranscription() {
        assertThat(AliyunAsrUtils.parseSentences("{\"duration\":0.1,\"text\":\"\"}")).isEqualTo(List.of());
    }
}
