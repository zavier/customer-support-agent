package com.github.zavier.customer.support.agent.node;

import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.MessageAgentState;
import com.github.zavier.customer.support.agent.MessageClassification;
import com.github.zavier.customer.support.agent.constant.Urgency;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;

@Component
public class ClassifyIntentCmdNode implements CommandAction<MessageAgentState> {

    private ChatClient chatClient;

    private PromptTemplate promptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template("""
                你是一个客户助手，现在需要分析客户的请求消息，对其进行分类：

                客户消息: <messageContent>
                客户名称: <userName>

                提供分类信息，包括意图、紧急程度、主题 和总结
                """)
            .build();

    public ClassifyIntentCmdNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public Command apply(MessageAgentState state, RunnableConfig config) throws Exception {
        final MessageClassification classification = chatClient.prompt(promptTemplate.render(state.data()))
                .call()
                .entity(MessageClassification.class);
        Assert.notNull(classification, "classification cannot be null");

        String gotoNode;
        if (classification.intent() == Intent.BILLING || classification.urgency() == Urgency.CRITICAL) {
            gotoNode = "humanReview";
        } else if (classification.intent() == Intent.QUESTION || classification.intent() == Intent.FEATURE) {
            gotoNode = "searchDocumentation";
        } else if (classification.intent() == Intent.BUG) {
            gotoNode = "bugTracking";
        } else {
            gotoNode = "draftResponse";
        }

        return new Command(gotoNode, Map.of("classification", classification));
    }
}
