package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatResponse {
    private String reply;
    private boolean enabled;
}
