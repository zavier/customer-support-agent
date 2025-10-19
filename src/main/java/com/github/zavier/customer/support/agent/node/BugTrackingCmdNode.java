package com.github.zavier.customer.support.agent.node;

import com.github.zavier.customer.support.agent.MessageAgentState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugTrackingCmdNode implements CommandAction<MessageAgentState> {

    @Override
    public Command apply(MessageAgentState state, RunnableConfig config) throws Exception {
        // TODO 添加/更新Bug跟踪逻辑
        String ticketId = "BUG-12345";

        return new Command("draftResponse",
                // 赋值给searchResult?  currentStep没这个状态？
                Map.of("searchResults", List.of("Bug Ticket " + ticketId + "created"),
                        "currentStep", "bugTracked"));
    }
}
