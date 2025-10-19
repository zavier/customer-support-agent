package com.github.zavier.customer.support.web;

import com.github.zavier.customer.support.agent.CustomerSupportGraph;
import com.github.zavier.customer.support.agent.MessageAgentState;
import com.github.zavier.customer.support.agent.MessageClassification;
import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.constant.Urgency;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.github.benmanes.caffeine.cache.Cache;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Resource
    private CustomerSupportGraph customerSupportGraph;

    @Resource
    private ChatWebSocketHandler webSocketHandler;

    // 使用 Caffeine 缓存存储会话状态
    @Resource
    private Cache<String, ChatSession> chatSessionCache;

    @Data
    public static class ChatMessage {
        private String id;
        private String content;
        private String type; // "user" or "assistant"
        private long timestamp;
        private String status; // "sending", "sent", "waiting_human", "completed"
        private MessageClassification classification;
    }

    @Data
    public static class ChatSession {
        private String sessionId;
        private String userName;
        private boolean isPausedForHuman;
        private boolean isTyping;
        private long lastAccessTime; // 最后访问时间
        private long creationTime;   // 创建时间
    }

    @Data
    public static class SendMessageRequest {
        private String message;
        private String userName;
        private String sessionId;
    }

    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody SendMessageRequest request) {
        log.info("收到消息: {} from user: {}", request.getMessage(), request.getUserName());

        // 创建或获取会话
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        long currentTime = System.currentTimeMillis();

        ChatSession session = chatSessionCache.get(sessionId, id -> {
            ChatSession newSession = new ChatSession();
            newSession.setSessionId(id);
            newSession.setUserName(request.getUserName());
            newSession.setPausedForHuman(false);
            newSession.setTyping(false);
            newSession.setCreationTime(currentTime);
            newSession.setLastAccessTime(currentTime);
            return newSession;
        });

        // 更新最后访问时间
        session.setLastAccessTime(currentTime);
        // 重新放入缓存以更新访问时间
        chatSessionCache.put(sessionId, session);

        // 创建用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setContent(request.getMessage());
        userMessage.setType("user");
        userMessage.setTimestamp(System.currentTimeMillis());
        userMessage.setStatus("sent");

        try {
            // 处理消息
            Map<String, Object> input = Map.of(
                    "messageContent", request.getMessage(),
                    "userName", request.getUserName()
            );

            var invokeConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            Optional<MessageAgentState> stateOptional = customerSupportGraph.run(input, invokeConfig);

            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setId(UUID.randomUUID().toString());
            assistantMessage.setType("assistant");
            assistantMessage.setTimestamp(System.currentTimeMillis());

            if (stateOptional.isPresent()) {
                MessageAgentState state = stateOptional.get();
                assistantMessage.setContent(state.draftResponse());
                assistantMessage.setClassification(state.classification().orElse(null));

                // 检查是否需要人工审核
                if (state.classification().isPresent()) {
                    MessageClassification classification = state.classification().get();
                    if (needsHumanReview(classification)) {
                        assistantMessage.setStatus("waiting_human");
                        session.setPausedForHuman(true);
                        log.info("消息需要人工审核: {}", request.getMessage());

                        // 通过WebSocket发送人工审核通知
                        ChatWebSocketHandler.ChatMessage wsMessage = new ChatWebSocketHandler.ChatMessage();
                        wsMessage.setType("human_review");
                        wsMessage.setSessionId(sessionId);
                        wsMessage.setUserName(request.getUserName());
                        wsMessage.setContent(state.draftResponse());
                        wsMessage.setClassification(classification);
                        wsMessage.setTimestamp(System.currentTimeMillis());
                        webSocketHandler.broadcastMessage(wsMessage);
                    } else {
                        assistantMessage.setStatus("completed");
                        session.setPausedForHuman(false);
                    }
                } else {
                    assistantMessage.setStatus("completed");
                }
            } else {
                assistantMessage.setContent("抱歉，我无法处理您的消息。");
                assistantMessage.setStatus("completed");
            }

            return ResponseEntity.ok(assistantMessage);

        } catch (Exception e) {
            log.error("处理消息时发生错误", e);
            ChatMessage errorMessage = new ChatMessage();
            errorMessage.setId(UUID.randomUUID().toString());
            errorMessage.setContent("抱歉，系统出现了一些问题，请稍后再试。");
            errorMessage.setType("assistant");
            errorMessage.setTimestamp(System.currentTimeMillis());
            errorMessage.setStatus("error");
            return ResponseEntity.ok(errorMessage);
        }
    }

    @PostMapping("/resume")
    public ResponseEntity<ChatMessage> resumeWithHumanFeedback(@RequestParam String sessionId,
                                                               @RequestParam String feedback) {
        log.info("恢复会话 {} 人工反馈: {}", sessionId, feedback);

        ChatSession session = chatSessionCache.getIfPresent(sessionId);
        if (session == null) {
            return ResponseEntity.badRequest().build();
        }

        // 更新最后访问时间
        session.setLastAccessTime(System.currentTimeMillis());
        chatSessionCache.put(sessionId, session);

        try {
            var invokeConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            Optional<MessageAgentState> stateOptional = customerSupportGraph.resume(invokeConfig, feedback);

            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setId(UUID.randomUUID().toString());
            assistantMessage.setType("assistant");
            assistantMessage.setTimestamp(System.currentTimeMillis());

            if (stateOptional.isPresent()) {
                MessageAgentState state = stateOptional.get();
                assistantMessage.setContent(state.draftResponse());
                assistantMessage.setStatus("completed");
                session.setPausedForHuman(false);
            } else {
                assistantMessage.setContent("无法处理人工反馈");
                assistantMessage.setStatus("error");
            }

            return ResponseEntity.ok(assistantMessage);

        } catch (Exception e) {
            log.error("处理人工反馈时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ChatSession> getSession(@PathVariable String sessionId) {
        ChatSession session = chatSessionCache.getIfPresent(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        // 更新最后访问时间
        session.setLastAccessTime(System.currentTimeMillis());
        chatSessionCache.put(sessionId, session);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/session/{sessionId}/typing")
    public ResponseEntity<Void> setTyping(@PathVariable String sessionId, @RequestParam boolean typing) {
        ChatSession session = chatSessionCache.getIfPresent(sessionId);
        if (session != null) {
            session.setTyping(typing);
            // 更新最后访问时间
            session.setLastAccessTime(System.currentTimeMillis());
            chatSessionCache.put(sessionId, session);
        }
        return ResponseEntity.ok().build();
    }

    private boolean needsHumanReview(MessageClassification classification) {
        return classification.intent() == Intent.BILLING ||
               classification.urgency() == Urgency.CRITICAL;
    }

    /**
     * 手动清理所有会话的管理接口
     */
    @PostMapping("/clear-sessions")
    public ResponseEntity<Map<String, Object>> clearSessions() {
        long beforeCount = chatSessionCache.estimatedSize();
        chatSessionCache.invalidateAll();
        long afterCount = chatSessionCache.estimatedSize();

        return ResponseEntity.ok(Map.of(
                "removedCount", beforeCount - afterCount,
                "activeSessions", afterCount,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/sessions-stats")
    public ResponseEntity<Map<String, Object>> getSessionsStats() {
        long totalSessions = chatSessionCache.estimatedSize();
        var stats = chatSessionCache.stats();

        // 获取所有会话统计
        long pausedForHumanCount = chatSessionCache.asMap().values().stream()
                .filter(ChatSession::isPausedForHuman)
                .count();

        return ResponseEntity.ok(Map.of(
                "totalSessions", totalSessions,
                "pausedForHuman", pausedForHumanCount,
                "hitRate", stats.hitRate(),
                "missRate", stats.missRate(),
                "requestCount", stats.requestCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}