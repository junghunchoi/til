package com.emoney.cs.common.designpattern.yalco._15_builder.ex02;

public class Main {
    public static void main(String[] args) {

        HttpRequest getRequest = new HttpRequest.Builder(
                "GET", "https://example.com/api")
                .addHeader("Authorization", "Bearer token")
                .addParameter("query", "builder-pattern")
                .build();

        HttpRequest postRequest = new HttpRequest.Builder(
                "POST", "https://example.com/api")
                .addHeader("Authorization", "Bearer token")
                .setBody("{ \"name\": \"John\", \"age\": 30 }")
                .build();

        System.out.println(getRequest);
        System.out.println(postRequest);
    }
}