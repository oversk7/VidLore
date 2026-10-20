package com.example.server.dto;

import java.util.List;

/**
 * 将视频中的语音和画面信息按时间轴整理成 Agent 可消费的统一上下文。
 */
public record VideoContext(
        String source,
        String userGoal,
        List<VideoSegment> segments
) {
    public VideoContext {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("video source is required");
        userGoal = userGoal == null ? "" : userGoal.trim();
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    /**
     * transcriptTimesMs 与 transcript 按行一一对应，是每句语音的开始时间；
     * ocrTimesMs 与 ocrTexts 一一对应，是每张关键帧的时间。
     * 旧版缓存里没有这两个字段，数量对不上时一律按片段起始时间处理。
     */
    public record VideoSegment(
            long startMs,
            long endMs,
            String transcript,
            List<String> ocrTexts,
            List<String> evidenceFrames,
            List<Long> transcriptTimesMs,
            List<Long> ocrTimesMs
    ) {
        public VideoSegment {
            if (startMs < 0 || endMs <= startMs) throw new IllegalArgumentException("invalid segment range");
            transcript = transcript == null ? "" : transcript.trim();
            ocrTexts = ocrTexts == null ? List.of() : List.copyOf(ocrTexts);
            evidenceFrames = evidenceFrames == null ? List.of() : List.copyOf(evidenceFrames);
            transcriptTimesMs = transcriptTimesMs == null ? List.of() : List.copyOf(transcriptTimesMs);
            ocrTimesMs = ocrTimesMs == null ? List.of() : List.copyOf(ocrTimesMs);
        }

        public VideoSegment(long startMs, long endMs, String transcript,
                            List<String> ocrTexts, List<String> evidenceFrames) {
            this(startMs, endMs, transcript, ocrTexts, evidenceFrames, List.of(), List.of());
        }
    }

    public String transcriptText() {
        return segments.stream()
                .map(VideoSegment::transcript)
                .filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
