package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.entity.MediaFile;
import com.example.server.dto.VideoContext;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.utils.MinioUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaFileMapper mediaFileMapper;
    private final StringRedisTemplate redisTemplate;
    private final MinioUtils minioUtils;
    private final ObjectMapper objectMapper;
    private final AgentCheckpointService checkpointService;
    private final AgentTelemetry telemetry;
    private final QdrantVectorStore vectorStore;
    private final VideoContextService videoContextService;

    private static final String MEDIA_MD5_KEY_PREFIX = "media:md5:";
    private static final Set<String> VIDEO_SUFFIXES = Set.of(
            ".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v");
    private static final int MAX_NAME_CHARS = 100;
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[\\p{Cntrl}\\\\/:*?\"<>|]");

    public MediaService(MediaFileMapper mediaFileMapper,
                        StringRedisTemplate redisTemplate,
                        MinioUtils minioUtils,
                        ObjectMapper objectMapper,
                        AgentCheckpointService checkpointService,
                        AgentTelemetry telemetry,
                        QdrantVectorStore vectorStore,
                        VideoContextService videoContextService) {
        this.mediaFileMapper = mediaFileMapper;
        this.redisTemplate = redisTemplate;
        this.minioUtils = minioUtils;
        this.objectMapper = objectMapper;
        this.checkpointService = checkpointService;
        this.telemetry = telemetry;
        this.vectorStore = vectorStore;
        this.videoContextService = videoContextService;
    }

    public String calculateMd5(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return calculateMd5(inputStream);
        }
    }

    public String calculateMd5(File file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return calculateMd5(inputStream);
        }
    }

    public void rememberContentHash(Long mediaId, String md5) {
        if (mediaId == null || md5 == null || md5.isBlank()) return;
        try {
            redisTemplate.opsForValue().set(MEDIA_MD5_KEY_PREFIX + mediaId, md5);
        } catch (RuntimeException e) {
            log.warn("media_hash_cache_write_failed mediaId={}", mediaId, e);
        }
    }

    public MediaFile saveUploadedMedia(String filename, String fileUrl, Long userId, String md5) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFilename(normalizeVideoFilename(filename));
        mediaFile.setFilePath(fileUrl);
        mediaFile.setStatus("COMPLETED");
        mediaFile.setUploadTime(LocalDateTime.now());
        mediaFile.setUserId(userId);
        mediaFile.setContentHash(md5);
        try {
            mediaFileMapper.insert(mediaFile);
            rememberContentHash(mediaFile.getId(), md5);
            invalidateUserList(userId);
            return mediaFile;
        } catch (RuntimeException e) {
            removeUploadedObject(fileUrl, e);
            throw e;
        }
    }

    public List<MediaFile> listByUser(Long userId) {
        String cacheKey = null;
        try {
            cacheKey = currentListCacheKey(userId);
            String cached = cacheKey == null ? null : redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<MediaFile>>() { });
            }
        } catch (Exception e) {
            log.warn("media_list_cache_read_failed userId={}", userId, e);
        }

        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        List<MediaFile> mediaFiles = mediaFileMapper.selectList(
                query.select("id", "filename", "status", "cover_url", "upload_time")
                        .eq("user_id", userId)
                        .orderByDesc("id"));
        try {
            // Keep the generation captured before the DB query. A concurrent mutation
            // moves future readers to a new key even if this old query finishes later.
            if (cacheKey != null) redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(mediaFiles), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("media_list_cache_write_failed userId={}", userId, e);
        }
        return mediaFiles;
    }

    public String contentHash(Long mediaId) {
        try {
            String cached = redisTemplate.opsForValue().get(MEDIA_MD5_KEY_PREFIX + mediaId);
            if (cached != null && !cached.isBlank()) return cached;
        } catch (RuntimeException e) {
            log.warn("media_hash_cache_read_failed mediaId={}", mediaId, e);
        }

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        String persisted = mediaFile == null ? null : mediaFile.getContentHash();
        rememberContentHash(mediaId, persisted);
        return persisted;
    }

    public void deleteOwnedMedia(Long mediaId, Long userId) {
        MediaFile mediaFile = requireOwnedMedia(mediaId, userId);
        mediaFileMapper.deleteById(mediaId);
        if (mediaFile.getFilePath() != null && mediaFile.getFilePath().startsWith("http")) {
            try {
                minioUtils.removeFile(mediaFile.getFilePath());
            } catch (RuntimeException e) {
                log.warn("media_object_cleanup_failed mediaId={} path={}",
                        mediaId, mediaFile.getFilePath(), e);
            }
        }
        purgeRuntimeArtifacts(mediaId);
        invalidateUserList(userId);
    }

    public boolean exists(Long mediaId) {
        return mediaId != null && mediaFileMapper.selectById(mediaId) != null;
    }

    public void purgeRuntimeArtifacts(Long mediaId) {
        VideoContext context = null;
        try {
            context = checkpointService.loadContext(mediaId);
        } catch (RuntimeException e) {
            log.warn("media_evidence_manifest_read_failed mediaId={}", mediaId, e);
        }
        VideoContext evidenceContext = context;
        cleanupArtifact(mediaId, "evidence_frames", () -> videoContextService.deleteEvidenceFrames(evidenceContext));
        cleanupArtifact(mediaId, "redis", () -> redisTemplate.delete(List.of(
                    MEDIA_MD5_KEY_PREFIX + mediaId,
                    "transcription:active:" + mediaId,
                    "transcription:state:" + mediaId)));
        cleanupArtifact(mediaId, "checkpoints", () -> checkpointService.deleteMedia(mediaId));
        cleanupArtifact(mediaId, "telemetry", () -> telemetry.deleteTask(mediaId));
        cleanupArtifact(mediaId, "vectors", () -> vectorStore.deleteMedia(mediaId));
    }

    private void cleanupArtifact(Long mediaId, String artifact, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException e) {
            log.warn("media_runtime_cleanup_failed mediaId={} artifact={}", mediaId, artifact, e);
        }
    }

    public void invalidateUserList(Long userId) {
        if (userId == null) return;
        try {
            String generationKey = userListKey(userId) + ":generation";
            String previous = redisTemplate.opsForValue().get(generationKey);
            redisTemplate.opsForValue().set(generationKey, UUID.randomUUID().toString());
            if (previous != null) redisTemplate.delete(userListKey(userId) + ":" + previous);
        } catch (RuntimeException e) {
            log.warn("media_list_cache_invalidation_failed userId={}", userId, e);
        }
    }

    public String readableSource(String source) {
        return minioUtils.readableSource(source);
    }

    public String normalizeVideoFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("视频文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new IllegalArgumentException("视频文件名无效或过长");
        }
        String suffix = fileSuffix(normalized).toLowerCase(java.util.Locale.ROOT);
        if (!VIDEO_SUFFIXES.contains(suffix)) {
            throw new IllegalArgumentException("仅支持 MP4、MOV、MKV、AVI、WEBM 和 M4V 视频");
        }
        return normalized;
    }

    /**
     * 把视频标题或用户输入的名称整理成文件名：去掉控制字符和路径、文件系统保留字符，
     * 合并空白，最长 100 字，再接上扩展名。整理后为空时返回 null。
     */
    public String nameToFilename(String name, String suffix) {
        if (name == null) return null;
        String cleaned = UNSAFE_NAME_CHARS.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").strip();
        // 用户在改名框里连扩展名一起输入时，不要变成 xxx.mp4.mp4
        if (cleaned.toLowerCase(java.util.Locale.ROOT).endsWith(suffix.toLowerCase(java.util.Locale.ROOT))) {
            cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).strip();
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_CHARS) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_CHARS)).strip();
        }
        return cleaned.isEmpty() ? null : cleaned + suffix;
    }

    /** 只改展示用的文件名；MinIO 里的对象路径是 UUID，不受影响。 */
    public MediaFile renameOwnedMedia(Long mediaId, Long userId, String name) {
        MediaFile mediaFile = requireOwnedMedia(mediaId, userId);
        String filename = nameToFilename(name, fileSuffix(mediaFile.getFilename()));
        if (filename == null) throw new IllegalArgumentException("视频名称不能为空");

        MediaFile update = new MediaFile();
        update.setId(mediaId);
        update.setFilename(filename);
        if (mediaFileMapper.updateById(update) == 0) throw new NoSuchElementException("文件不存在");
        // 先改数据库，再让列表缓存失效，下次读列表会从数据库重新加载
        invalidateUserList(userId);
        mediaFile.setFilename(filename);
        return mediaFile;
    }

    public MediaFile requireOwnedMedia(Long mediaId, Long userId) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) throw new NoSuchElementException("文件不存在");
        if (!Objects.equals(mediaFile.getUserId(), userId)) {
            throw new SecurityException("无权访问该文件");
        }
        return mediaFile;
    }

    private String calculateMd5(InputStream inputStream) throws IOException {
        MessageDigest digest = md5Digest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest md5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    private String userListKey(Long userId) {
        return "media:list:v3:user:" + userId;
    }

    private String currentListCacheKey(Long userId) {
        String generationKey = userListKey(userId) + ":generation";
        String generation = redisTemplate.opsForValue().get(generationKey);
        if (generation == null) {
            String fresh = UUID.randomUUID().toString();
            generation = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(generationKey, fresh))
                    ? fresh : redisTemplate.opsForValue().get(generationKey);
        }
        return generation == null ? null : userListKey(userId) + ":" + generation;
    }

    private String fileSuffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private void removeUploadedObject(String fileUrl, RuntimeException originalError) {
        try {
            minioUtils.removeFile(fileUrl);
        } catch (RuntimeException cleanupError) {
            originalError.addSuppressed(cleanupError);
            log.warn("uploaded_object_rollback_failed path={}", fileUrl, cleanupError);
        }
    }
}
