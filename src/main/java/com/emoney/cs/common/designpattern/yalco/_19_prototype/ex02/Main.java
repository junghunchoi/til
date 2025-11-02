package com.emoney.cs.common.designpattern.yalco._19_prototype.ex02;

// Usage example
public class Main {
    public static void main(String[] args) {
        DocumentTemplateManager.addTemplate(
                "welcome",
                new TextDocument("Welcome, {name}!"));
        DocumentTemplateManager.addTemplate(
                "meeting",
                new TextDocument("Meeting scheduled on {date} at {time}"));

        Document welcomeDoc = DocumentTemplateManager
                .createDocument("welcome");
        welcomeDoc.setContent(
                welcomeDoc.getContent().replace("{name}", "John Doe"));

        System.out.println(
                "Welcome document: " + welcomeDoc.getContent());

        Document meetingDoc = DocumentTemplateManager
                .createDocument("meeting");
        meetingDoc.setContent(meetingDoc.getContent()
                .replace("{date}", "2024-10-01")
                .replace("{time}", "14:00"));

        System.out.println(
                "Meeting document: " + meetingDoc.getContent());
    }
}