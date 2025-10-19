package com.github.zavier.customer.support.web;

import com.github.zavier.customer.support.agent.CustomerSupportGraph;
import com.github.zavier.customer.support.agent.MessageAgentState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
public class DemoController {

    @Resource
    private CustomerSupportGraph customerSupportGraph;

    @GetMapping("run")
    public String test() {
        // Input
        Map<String, Object> initialInput = Map.of(
                "messageContent", "I was charged twice for my subscription! This is urgent!",
                "userName", "customer001");

        // Thread
        var invokeConfig = RunnableConfig.builder()
                .threadId("customer001")
                .build();

        final Optional<MessageAgentState> stateOptional = customerSupportGraph.run(initialInput, invokeConfig);
        final String s = stateOptional.map(MessageAgentState::draftResponse).orElse("no content");
        log.info("Draft response: {}", s);
        return s;
    }

    @GetMapping("resume")
    public String resume(@RequestParam String feedback) {
        // Thread
        var invokeConfig = RunnableConfig.builder()
                .threadId("customer001")
                .build();

        final Optional<MessageAgentState> stateOptional = customerSupportGraph.resume(invokeConfig, feedback);
        final String s = stateOptional.map(MessageAgentState::draftResponse).orElse("no content");
        log.info("Draft response: {}", s);
        return s;
    }
}
