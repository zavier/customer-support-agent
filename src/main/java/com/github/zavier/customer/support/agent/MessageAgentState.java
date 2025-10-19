package com.github.zavier.customer.support.agent;

import org.bsc.langgraph4j.state.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MessageAgentState extends AgentState {

    // 原始消息数据
    private String messageContent;
    private String userName;

    // 分类结果
    private MessageClassification classification;

    // 原始搜索结果
    private List<String> searchResults;
    private Map<String, String> customerHistory;

    // 生成结果
    private String draftResponse;

    private String humanDecision;

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "searchResults", Channels.base((oldValue, newValue) -> newValue)
    );

    public MessageAgentState(Map<String, Object> initData) {
        super(initData);
    }





    public String messageContent() {
        return this.<String>value("messageContent").orElse("");
    }

    public Optional<MessageClassification> classification() {
        return value("classification");
    }

    public List<String> searchResults() {
        return this.<List<String>>value("searchResults").orElse(List.of());
    }

    public Map<String, String> customerHistory() {
        return this.<Map<String, String>>value("customerHistory").orElse(Map.of());
    }

    public String draftResponse() {
        return this.<String>value("draftResponse").orElse("");
    }

    public String humanDecision() {
        return this.<String>value("humanDecision").orElse("");
    }
}
