package com.shorturl;

import java.lang.management.ManagementFactory;
import java.util.TimeZone;

public class TImeTest {

    public static void main(String[] args) {

        System.out.println("Default TZ = " + TimeZone.getDefault().getID());
        System.out.println("VM Args = " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("After Change = " + TimeZone.getDefault().getID());

    }
}
