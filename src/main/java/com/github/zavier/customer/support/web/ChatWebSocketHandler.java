package com.github.zavier.customer.support.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zavier.customer.support.agent.MessageClassification;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class ChatMessage {
        private String type; // "message", "typing", "human_review", "status"
        private String sessionId;
        private String userName;
        private String content;
        private String status;
        private MessageClassification classification;
        private long timestamp;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket连接建立: {}", sessionId);

        // 发送连接确认消息
        ChatMessage confirmMessage = new ChatMessage();
        confirmMessage.setType("status");
        confirmMessage.setContent("connected");
        confirmMessage.setTimestamp(System.currentTimeMillis());
        sendMessage(session, confirmMessage);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        try {
            String payload = message.getPayload().toString();
            ChatMessage wsMessage = objectMapper.readValue(payload, ChatMessage.class);

            log.info("收到WebSocket消息: {}", wsMessage.getType());

            switch (wsMessage.getType()) {
                case "typing":
                    handleTypingMessage(sessionId, wsMessage);
                    break;
                case "message":
                    // 这个通过REST API处理，这里只是记录
                    log.info("用户消息: {}", wsMessage.getContent());
                    break;
                default:
                    log.warn("未知消息类型: {}", wsMessage.getType());
            }

        } catch (Exception e) {
            log.error("处理WebSocket消息时发生错误", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误", exception);
        String sessionId = session.getId();
        sessions.remove(sessionId);
        userSessionMap.remove(sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        userSessionMap.remove(sessionId);
        log.info("WebSocket连接关闭: {} - {}", sessionId, closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void handleTypingMessage(String sessionId, ChatMessage message) {
        // 广播打字状态给其他用户（如果需要的话）
        if (message.getUserName() != null) {
            userSessionMap.put(sessionId, message.getUserName());
        }
    }

    public void broadcastMessage(ChatMessage message) {
        sessions.values().forEach(session -> {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("广播消息时发生错误", e);
            }
        });
    }

    public void sendMessageToSession(String sessionId, ChatMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("发送消息到会话时发生错误", e);
            }
        }
    }

    private void sendMessage(WebSocketSession session, ChatMessage message) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (IOException e) {
            log.error("发送WebSocket消息时发生错误", e);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}