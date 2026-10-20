package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.dto.TranscriptSegment;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AliyunAsrUtils {

    private static final Logger log = LoggerFactory.getLogger(AliyunAsrUtils.class);
    private static final int MAX_ATTEMPTS = 3;

    private final String apiKey;
    private final String transcriptionUrl;
    private final String model;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build();

    public AliyunAsrUtils(@Value("${ai.asr.api-key}") String apiKey,
                          @Value("${ai.asr.url}") String transcriptionUrl,
                          @Value("${ai.asr.model}") String model) {
        this.apiKey = apiKey;
        this.transcriptionUrl = transcriptionUrl;
        this.model = model;
    }

    /** 返回按句切好的转写，时间相对于这个音频文件的开头。 */
    public List<TranscriptSegment> audioToSentences(String filePath) {
        File file = new File(filePath);
        // 这里的音频是本流水线上一步用 ffmpeg 切出来的，缺失属于「意外状态」而非「调用方参数错误」，
        // 重跑流水线可以重新生成，因此用 IllegalStateException 保持它可重试，
        // 不要和下面表示「请求本身不合法、重试无意义」的 IllegalArgumentException 混用。
        if (!file.isFile()) throw new IllegalStateException("ASR audio file does not exist");

        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                List<TranscriptSegment> sentences = parseSentences(execute(file));
                if (sentences.isEmpty()) throw new IllegalStateException("ASR 返回空文本");
                return sentences;
            } catch (IOException e) {
                lastError = e;
                log.warn("asr_attempt_failed attempt={} file={}", attempt + 1, file.getName(), e);
                if (attempt < MAX_ATTEMPTS - 1) waitBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("ASR 调用失败，已达到最大重试次数", lastError);
    }

    private String execute(File file) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .addFormDataPart("model", model)
                // 默认只返回整段文字；verbose_json 才会带上每句话的起止时间。
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "segment")
                .build();
        Request request = new Request.Builder()
                .url(transcriptionUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (response.isSuccessful()) return body;
            if (response.code() == 429 || response.code() >= 500) {
                throw new RetryableAsrException("ASR transient HTTP " + response.code());
            }
            // 非 429/5xx 的失败源于请求本身（参数、鉴权、音频格式不支持），重投多少次都不会变好。
            // 用 IllegalArgumentException 让上层能识别为「不可重试」，避免整条流水线空转重试。
            throw new IllegalArgumentException("ASR request rejected with HTTP " + response.code());
        }
    }

    /** 有 segments 就按句返回；接口没给句级时间时，退回整段文字、时间记为文件开头。 */
    static List<TranscriptSegment> parseSentences(String body) {
        JSONObject json = JSON.parseObject(body);
        if (json == null) return List.of();
        List<TranscriptSegment> sentences = new ArrayList<>();
        JSONArray segments = json.getJSONArray("segments");
        if (segments != null) {
            for (int i = 0; i < segments.size(); i++) {
                JSONObject segment = segments.getJSONObject(i);
                String text = segment.getString("text");
                if (text == null || text.isBlank()) continue;
                long startMs = Math.max(0, Math.round(segment.getDoubleValue("start") * 1000));
                long endMs = Math.max(startMs + 1, Math.round(segment.getDoubleValue("end") * 1000));
                sentences.add(new TranscriptSegment(startMs, endMs, text));
            }
        }
        if (sentences.isEmpty()) {
            String text = json.getString("text");
            if (text != null && !text.isBlank()) {
                long endMs = Math.max(1, Math.round(json.getDoubleValue("duration") * 1000));
                sentences.add(new TranscriptSegment(0, endMs, text));
            }
        }
        return sentences;
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(1_000L << attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ASR retry interrupted", e);
        }
    }

    private static class RetryableAsrException extends IOException {
        private RetryableAsrException(String message) {
            super(message);
        }
    }
}
