package dev.joe.aimemoryservice.service;

public final class MemoryEmbeddingTextBuilder {
    private MemoryEmbeddingTextBuilder() {}

    public static String build(String title, String content, String evidence) {
        StringBuilder text = new StringBuilder()
            .append("Title: ")
            .append(title)
            .append("\n\nContent:\n")
            .append(content);

        if(evidence != null)
            text.append("\n\nEvidence:\n").append(evidence);

        return text.toString();
    }
}
