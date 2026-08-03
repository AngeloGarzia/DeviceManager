package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailTestResponse {
    private boolean success;
    private String message;
    private String to;
}
