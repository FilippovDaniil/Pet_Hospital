package com.hospital.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Сообщение не может быть пустым")
    @Size(max = 4000, message = "Сообщение не должно превышать 4000 символов")
    private String content;
}
