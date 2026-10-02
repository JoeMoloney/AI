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


def get_embedding(text: str) -> list[float]:
    payload = json.dumps({
        "model": OLLAMA_MODEL,
        "input": f"search_document: {text}",
    }).encode("utf-8")

    request = urllib.request.Request(
        OLLAMA_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(request) as response:
        data = json.load(response)

    embedding = data["embeddings"][0]

    if len(embedding) != 768:
        raise RuntimeError(
            f"Expected 768 dimensions, got {len(embedding)}"
        )

    return embedding


with psycopg.connect(DB_DSN) as conn:
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                id,
                title,
                content,
                evidence
            FROM memories
            WHERE embedding IS NULL
              AND status = 'active'
            ORDER BY id;
        """)

        memories = cur.fetchall()

        print(f"Found {len(memories)} memories without embeddings")

        for memory_id, title, content, evidence in memories:
            text = f"""
Title: {title}

Content:
{content}
"""

            if evidence:
                text += f"\nEvidence:\n{evidence}\n"

            embedding = get_embedding(text)

            # pgvector accepts its normal textual representation:
            # [0.1,0.2,...]
            vector_string = "[" + ",".join(
                str(value) for value in embedding
            ) + "]"

            cur.execute(
                """
                UPDATE memories
                SET embedding = %s::vector
                WHERE id = %s;
                """,
                (vector_string, memory_id),
            )

            print(f"Embedded memory {memory_id}: {title}")

    conn.commit()

print("Done.")