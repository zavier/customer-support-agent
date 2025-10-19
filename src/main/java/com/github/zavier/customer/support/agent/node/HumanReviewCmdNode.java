package com.github.zavier.customer.support.agent.node;

import com.github.zavier.customer.support.agent.MessageAgentState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HumanReviewCmdNode implements CommandAction<MessageAgentState> {
    @Override
    public Command apply(MessageAgentState state, RunnableConfig config) throws Exception {
        // 需要在此之前中断，获取用户输入，到这里时已经有用户输入信息

        final String humanDecision = state.humanDecision();
        if ("approved".equalsIgnoreCase(humanDecision)) {
            // TODO 这里可以加一个 editedResponse 参数，用于保存用户修改后的结果吗？
            return new Command(StateGraph.END, Map.of("draftResponse", state.draftResponse()));
        } else {
            return new Command(StateGraph.END, Map.of());
        }
    }
}
