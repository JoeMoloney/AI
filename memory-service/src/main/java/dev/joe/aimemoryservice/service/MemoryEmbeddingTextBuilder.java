package dev.joe.aimemoryservice.service;

/** Builds the stable text representation used to embed a memory. */
public final class MemoryEmbeddingTextBuilder {
    private MemoryEmbeddingTextBuilder() {}

    /**
     * Combines searchable memory fields in a labeled format.
     *
     * @param title normalized memory title
     * @param content normalized memory content
     * @param evidence optional normalized evidence
     * @return canonical text for {@link EmbeddingService#embedDocument(String)}
     */
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
