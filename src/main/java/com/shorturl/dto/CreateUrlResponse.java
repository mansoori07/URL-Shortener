package com.shorturl.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateUrlResponse {

    private String shortCode;

    private String shortUrl;

}
