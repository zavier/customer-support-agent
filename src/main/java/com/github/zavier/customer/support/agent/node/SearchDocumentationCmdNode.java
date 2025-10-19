package com.github.zavier.customer.support.agent.node;

import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.MessageAgentState;
import com.github.zavier.customer.support.agent.MessageClassification;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class SearchDocumentationCmdNode implements CommandAction<MessageAgentState> {

    @Retryable(maxAttempts = 3)
    @Override
    public Command apply(MessageAgentState state, RunnableConfig config) throws Exception {
        final Optional<MessageClassification> classificationOpt = state.classification();

        String query = classificationOpt.map(MessageClassification::intent).map(Intent::name).orElse("")
                + " "
                + classificationOpt.map(MessageClassification::topic).orElse("");

        List<String> searchResults;
        try {
            // TODO 实现自定义的搜索逻辑，存储原始结果而非结构化数据
            searchResults = List.of(
                    "Reset password via Settings > Security > Change Password",
                    "Password must be at least 12 characters",
                    "Include uppercase, lowercase, number and symbols"
            );
        } catch (Exception e) {
            log.error("Error while searching documentation for query: {}", query, e);
            searchResults = List.of("Search temporarily unavailable: " + e.getMessage());
        }

        return new Command("draftResponse", Map.of("searchResults", searchResults));
    }
}
