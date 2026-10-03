package dev.joe.aimemoryservice.service;

public interface EmbeddingService {
    float[] embedDocument(String text);
    float[] embedQuery(String text);
}
