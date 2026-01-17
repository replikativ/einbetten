# Einbetten - Wikipedia Semantic Search Demo

> **Semantic search over Wikipedia using FastEmbed, Proximum, and Datahike**

A demonstration of integrating vector search with Datalog queries, showcasing how to build a semantic search system over 2,000 Wikipedia articles with ~8,000 chunks.

## What is Einbetten?

Einbetten (German for "embedding") demonstrates how to combine:

- **Proximum**: Pure Clojure vector database with HNSW algorithm that implements standard Clojure collection protocols
- **Datahike**: Immutable Datalog database (Datomic-like) for structured metadata
- **FastEmbed**: Fast, local embedding generation (no API calls needed)

## Features

- **Semantic Search**: Find articles by meaning, not just keywords
- **Datalog Integration**: Combine vector search with powerful Datalog queries
- **Proximum as a Clojure Collection**: Vector database that implements `assoc`, `get`, `into`, `transient`
- **Immutable & Persistent**: Snapshot semantics with time-travel queries
- **Fast Local Embeddings**: No API calls, no cloud dependencies

## Quick Start

### Prerequisites

```bash
# Install Clojure CLI tools
# https://clojure.org/guides/install_clojure

# Ensure you have Python 3.9+
python3 --version

# Download the pre-computed corpus (~42MB)
wget https://github.com/replikativ/einbetten/releases/download/v1.0.0/einbetten-corpus-v1.tar.gz
tar -xzf einbetten-corpus-v1.tar.gz
```

### Setup

```bash
# Create Python environment and install FastEmbed
python3 -m venv .venv
.venv/bin/pip install fastembed

# Setup database and index (~2 minutes)
./setup.sh
```

This will:
- Create Datahike database with LMDB backend (beta)
- Load 2,000 Wikipedia articles
- Load ~8,000 text chunks
- Create Proximum HNSW vector index
- Load pre-computed embeddings (384-dimensional)
- Test semantic search

### Query Examples

Start a REPL:

```bash
clj -M:repl
```

#### Load the Setup Namespace

```clojure
;; The setup script defines conn, prox-idx, embedder, and search-similar-chunks
(require '[setup-corpus :refer [conn prox-idx embedder search-similar-chunks]])
```

#### Basic Semantic Search

```clojure
;; Search for chunks by meaning
(let [query "What is lazy evaluation in functional programming?"
      eids (search-similar-chunks prox-idx embedder query 5)]
  (d/q '[:find ?title ?text
         :in $ [?eid ...]
         :where
         [?eid :chunk/text ?text]
         [?eid :chunk/article ?article]
         [?article :article/title ?title]]
       @conn
       eids))
```

#### Search with Datalog Function Call

```clojure
;; Pass search function into Datalog query
(d/q '[:find ?title ?text
       :in $ ?search-fn ?query
       :where
       [(?search-fn ?query 5) [?eid ...]]
       [?eid :chunk/text ?text]
       [?eid :chunk/article ?article]
       [?article :article/title ?title]]
     @conn
     #(search-similar-chunks prox-idx embedder %1 %2)
     "machine learning algorithms")
```

See **[docs/datalog-semantic-search-patterns.md](docs/datalog-semantic-search-patterns.md)** for more query patterns.

## Proximum: Vector Database as Clojure Collection

Proximum implements standard Clojure protocols, making it feel like a native data structure:

```clojure
;; It's just a map!
(def idx (prox/create-index {:type :hnsw :dim 384 ...}))

;; assoc to add vectors
(def idx2 (assoc idx entity-id embedding-vector))

;; get to retrieve by ID
(get idx2 entity-id)  ; => vector

;; into for batch operations (uses transient internally!)
(def idx3 (into idx2 [[id1 vec1] [id2 vec2] ...]))

;; Immutable - each operation returns new index
(count idx)   ; => 0 (original unchanged!)
(count idx3)  ; => 2

;; Search for nearest neighbors
(prox/search idx3 query-vector 10)
; => [{:id id1 :distance 0.123} ...]
```

**Key benefits:**
- Standard Clojure idioms (`assoc`, `get`, `into`, `transient`)
- Immutable with snapshot semantics (time-travel!)
- Fast batch operations with `into`
- Clean integration with Clojure code

See **[docs/prox-protocol-tutorial.md](docs/prox-protocol-tutorial.md)** for a hands-on tutorial and **[demo-prox-protocols.clj](demo-prox-protocols.clj)** for an interactive REPL demo.

## Corpus Details

The corpus contains:
- **2,000 Wikipedia articles** across 8 categories
- **~8,000 text chunks** (512 tokens each with 128 token overlap)
- **Pre-computed embeddings** using BAAI/bge-small-en-v1.5 (384 dimensions)

Categories included:
- Programming languages
- Computer science
- Mathematics
- Physics
- Biology
- Chemistry
- History
- Geography

## Performance Metrics

- Setup time: ~8 seconds (with pre-computed embeddings)
- Embedding generation: ~13 chunks/sec (if regenerating)
- Vector insertion: ~8,000 vectors/sec
- Search latency: < 50ms for k=20
- Index size: ~12 MB for 8,000 vectors (384-dim)

## Project Structure

```
einbetten/
├── corpus/                    # Pre-computed corpus (download separately)
│   ├── articles.edn.gz       # Wikipedia articles
│   ├── chunks.edn.gz         # Text chunks
│   └── embeddings.edn.gz     # Pre-computed embeddings
├── data/                     # Created by setup.sh
│   ├── datahike/            # LMDB database
│   └── prox/                # Proximum vector index
├── docs/                     # Documentation
│   ├── datalog-semantic-search-patterns.md
│   └── prox-protocol-tutorial.md
├── scripts/
│   └── setup_corpus.clj     # Setup script
├── src/einbetten/
│   ├── chunking.clj         # Text chunking utilities
│   └── schema.clj           # Datahike schema
├── demo-prox-protocols.clj  # Interactive demo
├── setup.sh                 # Main setup script
└── deps.edn                 # Dependencies
```

## Documentation

- **[prox-protocol-tutorial.md](docs/prox-protocol-tutorial.md)**: Hands-on guide to Proximum's Clojure protocols
- **[datalog-semantic-search-patterns.md](docs/datalog-semantic-search-patterns.md)**: Query patterns and examples
- **[demo-prox-protocols.clj](demo-prox-protocols.clj)**: Interactive REPL demo

## Dependencies

- **[Datahike](https://github.com/replikativ/datahike)**: Immutable Datalog database (v0.7.1628)
- **[Datahike-LMDB](https://github.com/replikativ/datahike-lmdb)**: LMDB storage backend (beta)
- **[Proximum](https://github.com/replikativ/proximum)**: Pure Clojure vector database
- **[FastEmbed](https://github.com/qdrant/fastembed)**: Fast embedding generation
- **[libpython-clj](https://github.com/clj-python/libpython-clj)**: Python interop

## Use Cases

This demo showcases patterns useful for:
- Semantic search over documentation
- Question answering systems
- Content recommendation
- Knowledge base exploration
- RAG (Retrieval Augmented Generation) pipelines

## License

- **Code**: MIT
- **Wikipedia Content**: CC BY-SA 3.0

## Credits

Part of the [Replikativ](https://github.com/replikativ) ecosystem of immutable, distributed data structures.
