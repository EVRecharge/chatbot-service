package com.evcharging.chatbotservice.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SendMessageRequest {
    @NotBlank
    @Size(max = 2000, message = "Keep messages under 2000 characters")
    private String message;
}
