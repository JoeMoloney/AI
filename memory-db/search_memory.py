import json
import urllib.request
import psycopg

OLLAMA_URL = "http://localhost:11434/api/embed"
OLLAMA_MODEL = "nomic-embed-text"

DB_DSN = (
    "dbname=ai_memory "
    "user=ai_memory "
    "password=YOUR_PASSWORD "
    "host=127.0.0.1 "
    "port=5432"
)


def get_query_embedding(text: str) -> list[float]:
    payload = json.dumps({
        "model": OLLAMA_MODEL,
        "input": f"search_query: {text}",
    }).encode("utf-8")

    request = urllib.request.Request(
        OLLAMA_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(request) as response:
        data = json.load(response)

    return data["embeddings"][0]


query = "What made my local language model become slow after working for a while?"

embedding = get_query_embedding(query)

vector_string = "[" + ",".join(
    str(value) for value in embedding
) + "]"

with psycopg.connect(DB_DSN) as conn:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT
                id,
                title,
                content,
                1 - (embedding <=> %s::vector) AS similarity
            FROM memories
            WHERE status = 'active'
              AND embedding IS NOT NULL
            ORDER BY embedding <=> %s::vector
            LIMIT 5;
            """,
            (vector_string, vector_string),
        )

        for memory_id, title, content, similarity in cur.fetchall():
            print()
            print(f"[{memory_id}] {title}")
            print(f"Similarity: {similarity:.4f}")
            print(content)