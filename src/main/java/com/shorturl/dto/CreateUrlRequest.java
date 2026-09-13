package com.shorturl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUrlRequest {

    @NotBlank(message = "Original URL must not be blank")
    @Size(max = 2048, message = "Original URL must not exceed 2048 characters")
    private String originalUrl;

}
