package com.github.zavier.customer.support.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.constant.Urgency;
import com.github.zavier.customer.support.agent.node.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;

@Slf4j
@Component
public class CustomerSupportGraph {

    @Resource
    private ClassifyIntentCmdNode classifyIntentCmdNode;
    @Resource
    private SearchDocumentationCmdNode searchDocumentationCmdNode;
    @Resource
    private HumanReviewCmdNode humanReviewCmdNode;
    @Resource
    private DraftResponseCmdNode draftResponseCmdNode;
    @Resource
    private BugTrackingCmdNode bugTrackingCmdNode;

    @Resource
    private ObjectMapper objectMapper;


    private CompiledGraph<MessageAgentState> graph;

    @PostConstruct
    public void init() throws GraphStateException {
        final StateGraph<MessageAgentState> builder = new StateGraph<>(MessageAgentState.SCHEMA, MessageAgentState::new)
                .addNode("classifyIntent", command_async(classifyIntentCmdNode), mappings())
                .addNode("searchDocumentation", command_async(searchDocumentationCmdNode), mappings())
                .addNode("humanReview", command_async(humanReviewCmdNode), mappings())
                .addNode("draftResponse", command_async(draftResponseCmdNode), mappings())
                .addNode("bugTracking", command_async(bugTrackingCmdNode), mappings())

                .addEdge(StateGraph.START, "classifyIntent");

        final MemorySaver memorySaver = new MemorySaver();
        final CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(memorySaver)
                .interruptBefore("humanReview")
                .releaseThread(true)
                .build();

        graph = builder.compile(compileConfig);
    }

    public Optional<MessageAgentState> run(Map<String, Object> initData, RunnableConfig runnableConfig) {
        Assert.notNull(graph, "graph cannot be null");
        Assert.notNull(initData, "initData cannot be null");
        Assert.notNull(runnableConfig, "runnableConfig cannot be null");

        return graph.invoke(initData, runnableConfig);
    }

    public Optional<MessageAgentState> resume(RunnableConfig runnableConfig, String feedback) {
        Assert.notNull(graph, "graph cannot be null");
        Assert.notNull(runnableConfig, "runnableConfig cannot be null");

        try {
            var updateConfig = graph.updateState(runnableConfig, Map.of("humanDecision", feedback));
            return graph.invoke(GraphInput.resume(), updateConfig);
        } catch (Exception e) {
            log.error("resume updateState error", e);
            throw new RuntimeException("中断恢复异常");
        }
    }

    public String interruptMessage(RunnableConfig runnableConfig) {
        // interruptBefore humanReview 处理
        final StateSnapshot<MessageAgentState> stateSnapshot = graph.getState(runnableConfig);
        if (!"humanReview".equalsIgnoreCase(stateSnapshot.next())) {
            throw new IllegalArgumentException("当前不是humanReview中断, 不支持此类型");
        }

        final Optional<MessageClassification> classification = stateSnapshot.state().classification();
        Map<String, Object> interruptData = Map.of(
                "messageContext", stateSnapshot.state().messageContent(),
                "draftResponse", stateSnapshot.state().draftResponse(),
                "urgency", classification.map(MessageClassification::urgency).orElse(Urgency.MEDIUM),
                "intent", classification.map(MessageClassification::intent).map(Intent::name).orElse("unknow"),
                "action", "Please review and approve/edit this response"
        );
        try {
            return objectMapper.writeValueAsString(interruptData);
        } catch (JsonProcessingException e) {
            log.error("json转换异常 data:{}", interruptData, e);
            throw new RuntimeException("数据处理异常");
        }
    }


    public boolean isInterrupt(RunnableConfig runnableConfig) {
        // // interruptBefore humanReview 处理
        final StateSnapshot<MessageAgentState> stateSnapshot = graph.getState(runnableConfig);
        return "humanReview".equalsIgnoreCase(stateSnapshot.next());
    }

    private Map<String, String> mappings() {
        return EdgeMappings.builder()
                .toEND()
                .to("humanReview")
                .to("searchDocumentation")
                .to("bugTracking")
                .to("draftResponse")
                .build();
    }
}
