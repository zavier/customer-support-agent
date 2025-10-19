package com.github.zavier.customer.support.agent;

import com.github.zavier.customer.support.agent.constant.Intent;
import com.github.zavier.customer.support.agent.constant.Urgency;

import java.io.Serializable;

public record MessageClassification(Intent intent, Urgency urgency, String topic, String summary) implements Serializable {
    private static final long serialVersionUID = 1L;
}