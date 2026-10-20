package com.example.server.service;

import com.example.server.dto.AnalysisResult;
import com.example.server.dto.ExecutorDraft;
import com.example.server.dto.VideoContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 证据编号表：给每句转写、每行画面文字编一个编号，模型只引用编号，原文由代码按编号回填。
 *
 * <p>编号形如 {@code S120.T3}：S 后是片段起始秒，T 是该片段语音转写的第 3 句，O 是画面文字的第几行。
 * 每条证据的时间戳取这句话自己的开始时间（ASR 句级时间）或所在关键帧的时间，旧缓存没有时退回片段起始时间。
 * 用起始秒而不用片段序号，是因为每轮交给模型的只是检索出的子集，序号会随子集变化，
 * 而片段按固定时间窗切分，起始秒在整段视频里唯一且稳定。
 */
public final class EvidenceCatalog {

    static final int MAX_REFS_PER_CONCLUSION = 3;
    private static final int MIN_OCR_LINE_CHARS = 2;
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？!?；;])");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final String rendered;

    public record Entry(String ref, long timestampMs, String source, String text) {
    }

    /** unresolvedRefs 是模型给出、但编号表里不存在的编号，这些引用会被丢弃。 */
    public record Resolution(AnalysisResult result, List<String> unresolvedRefs) {
    }

    private EvidenceCatalog(VideoContext context) {
        StringBuilder out = new StringBuilder();
        for (VideoContext.VideoSegment segment : context.segments()) {
            String segmentRef = "S" + segment.startMs() / 1000;
            List<Entry> segmentEntries = new ArrayList<>();
            // 转写每行是 ASR 给出的一句（带开始时间），一行里可能还有多个句号，拆开后共用这一行的时间。
            List<String> transcriptLines = LINE_BREAK.splitAsStream(segment.transcript()).toList();
            int sentenceNo = 0;
            for (int i = 0; i < transcriptLines.size(); i++) {
                long timestampMs = timeAt(segment.transcriptTimesMs(), transcriptLines.size(), i, segment.startMs());
                for (String sentence : SENTENCE_END.split(transcriptLines.get(i))) {
                    String text = singleLine(sentence);
                    if (meaningfulChars(text) == 0) continue;
                    segmentEntries.add(new Entry(segmentRef + ".T" + ++sentenceNo, timestampMs, "ASR", text));
                }
            }
            // OCR 噪声行（如 "=]"）对模型没有信息量，只会浪费 Token，过滤掉；
            // 多张关键帧的重复行只保留一次，时间取最早出现的那一帧。
            Set<String> seenOcrLines = new HashSet<>();
            int lineNo = 0;
            for (int i = 0; i < segment.ocrTexts().size(); i++) {
                long timestampMs = timeAt(segment.ocrTimesMs(), segment.ocrTexts().size(), i, segment.startMs());
                for (String rawLine : LINE_BREAK.split(segment.ocrTexts().get(i))) {
                    String line = singleLine(rawLine);
                    if (meaningfulChars(line) < MIN_OCR_LINE_CHARS || !seenOcrLines.add(line)) continue;
                    segmentEntries.add(new Entry(segmentRef + ".O" + ++lineNo, timestampMs, "OCR", line));
                }
            }
            if (segmentEntries.isEmpty()) continue;

            out.append('[').append(segmentRef).append(' ')
                    .append(formatTime(segment.startMs())).append('-').append(formatTime(segment.endMs()))
                    .append("]\n");
            for (Entry entry : segmentEntries) {
                entries.putIfAbsent(entry.ref(), entry);
                out.append(entry.ref()).append(" [").append(formatTime(entry.timestampMs())).append("] ")
                        .append(entry.text()).append('\n');
            }
        }
        rendered = out.toString();
    }

    public static EvidenceCatalog of(VideoContext context) {
        return new EvidenceCatalog(context);
    }

    /** 交给模型的上下文：每条证据单独一行，行首是编号。 */
    public String render() {
        return rendered;
    }

    /** 把模型的“结论 + 编号”换成带原文、时间戳和来源的结构化结果；claim 直接取结论本身。 */
    public Resolution resolve(ExecutorDraft draft) {
        List<String> conclusions = new ArrayList<>();
        List<AnalysisResult.Evidence> evidence = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (ExecutorDraft.Conclusion conclusion : draft.conclusions()) {
            if (conclusion.text().isBlank()) continue;
            conclusions.add(conclusion.text());
            conclusion.refs().stream()
                    .map(EvidenceCatalog::normalizeRef)
                    .distinct()
                    .limit(MAX_REFS_PER_CONCLUSION)
                    .forEach(ref -> {
                        Entry entry = entries.get(ref);
                        if (entry == null) {
                            unresolved.add(ref);
                            return;
                        }
                        evidence.add(new AnalysisResult.Evidence(
                                entry.timestampMs(), entry.source(), entry.text(), conclusion.text()));
                    });
        }
        AnalysisResult result = new AnalysisResult(
                draft.title(), conclusions, evidence, draft.suggestions(), draft.sections());
        return new Resolution(result, List.copyOf(unresolved));
    }

    /** 容忍模型写成小写、带空格或方括号的编号，如 "[ s120.t3 ]"。 */
    private static String normalizeRef(String ref) {
        return ref.replaceAll("[\\s\\[\\]【】]", "").toUpperCase(Locale.ROOT);
    }

    /** 折叠原文里的换行：一条证据只占一行，原文里的文字无法伪装成一行带编号的证据。 */
    private static String singleLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** 时间列表和文本数量对得上才用（旧缓存没有时间），否则退回片段起始时间。 */
    private static long timeAt(List<Long> times, int expectedSize, int index, long fallbackMs) {
        return times.size() == expectedSize ? times.get(index) : fallbackMs;
    }

    private static int meaningfulChars(String text) {
        return (int) text.codePoints().filter(Character::isLetterOrDigit).count();
    }

    private static String formatTime(long timestampMs) {
        long seconds = timestampMs / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
