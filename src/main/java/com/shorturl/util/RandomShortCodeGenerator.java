package com.shorturl.util;

import java.security.SecureRandom;

public class RandomShortCodeGenerator {

    private static final String CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate(){
        StringBuilder code = new StringBuilder();

        for(int i = 0; i < CODE_LENGTH; i++){
            int index = random.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        return code.toString();
    }
}
