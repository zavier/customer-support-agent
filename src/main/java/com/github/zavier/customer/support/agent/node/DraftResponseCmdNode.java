package com.github.zavier.customer.support.agent.node;

import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.MessageAgentState;
import com.github.zavier.customer.support.agent.MessageClassification;
import com.github.zavier.customer.support.agent.constant.Urgency;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DraftResponseCmdNode implements CommandAction<MessageAgentState> {

    private ChatClient chatClient;

    public DraftResponseCmdNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    private PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template("""
                Draft a response to this customer:
                <messageContent>
                
                Message intent: <intent>
                Urgency level: <urgency>
                
                
                <contextSelections>
                
                Guidelines:
                - Be professional and helpful
                - Address their specific concern
                - Use the provide documentation when relevant
                
                """)
            .build();

    @Override
    public Command apply(MessageAgentState state, RunnableConfig config) throws Exception {
        List<String> contextSelections = new ArrayList<>();

        final List<String> searchResults = state.searchResults();
        if (!searchResults.isEmpty()) {
            String formattedDocs = searchResults.stream()
                    .map(result -> "- " + result)
                    .collect(Collectors.joining("\n"));
            contextSelections.add("Relevant documentation:\n" + formattedDocs);
        }

        final Map<String, String> customerHistoryMap = state.customerHistory();
        if (!customerHistoryMap.isEmpty()) {
            contextSelections.add("Customer tier:" + customerHistoryMap.getOrDefault("tier", "standard"));
        }

        Map<String, Object> promptDataMap = Map.of(
                "messageContent", state.messageContent(),
                "intent", state.classification().map(MessageClassification::intent).map(Intent::name).orElse("unknow"),
                "urgency", state.classification().map(MessageClassification::urgency).map(Urgency::name).orElse("medium"),
                "contextSelections", contextSelections.isEmpty() ? "" : String.join("\n", contextSelections)
        );
        final String response = chatClient.prompt(promptTemplate.render(promptDataMap))
                .call()
                .content();

        // 根据紧急程度和意图判断是否需要人工审核
        final boolean needReview = needReview(state);

        String gotoNode = needReview ? "humanReview" : StateGraph.END;

        return new Command(gotoNode, Map.of("draftResponse", response));
    }

    private static boolean needReview(MessageAgentState state) {
        final Optional<MessageClassification> classification = state.classification();
        if (classification.isEmpty()) {
            return false;
        }

        final boolean isUrgency = Set.of(Urgency.HIGH, Urgency.CRITICAL).contains(classification.get().urgency());
        final boolean isComplex = classification.get().intent() == Intent.COMPLEX;
        return isUrgency || isComplex;
    }
}
