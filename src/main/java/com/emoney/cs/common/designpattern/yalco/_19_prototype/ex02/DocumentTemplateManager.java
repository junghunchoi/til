package com.emoney.cs.common.designpattern.yalco._19_prototype.ex02;

import java.util.HashMap;
import java.util.Map;

// Template manager
class DocumentTemplateManager {
    private static final Map<String, Document> templates
            = new HashMap<>();

    public static void addTemplate(String name, Document doc) {
        templates.put(name, doc);
    }

    public static Document createDocument(String templateName) {
        Document template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException(
                    "Template not found: " + templateName);
        }
        return (Document) template.clone();
    }
}