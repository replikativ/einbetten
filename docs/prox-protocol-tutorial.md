# Prox Collection Protocols Tutorial

A hands-on guide to using Prox's Clojure collection protocols.

## Why This Matters

Prox implements standard Clojure protocols (`IPersistentMap`, `ILookup`, `ITransientMap`, etc.), making it feel like working with native Clojure data structures. You don't need to learn a special API - if you know how to work with Clojure maps, you already know how to use Prox!

## Quick Start: Load and Play

```bash
clj -M:repl
```

```clojure
(require '[proximum.core :as prox])

;; Create a tiny 3-dimensional index for learning
(def idx (prox/create-index {:type :hnsw
                              :dim 3
                              :M 8
                              :ef-construction 50
                              :store-config {:backend :memory
                                            :id (java.util.UUID/randomUUID)}
                              :capacity 100}))
```

## Level 1: It's Just a Map!

### Adding Vectors with `assoc`

```clojure
;; assoc works like with regular maps!
(def idx2 (assoc idx "doc-1" (float-array [1.0 0.0 0.0])))

(count idx)   ; => 0 (original unchanged - immutable!)
(count idx2)  ; => 1

;; Chain operations
(def idx3 (-> idx2
              (assoc "doc-2" (float-array [0.0 1.0 0.0]))
              (assoc "doc-3" (float-array [0.0 0.0 1.0]))))

(count idx3)  ; => 3
```

**Key insight**: Each `assoc` returns a NEW index. The original is unchanged. This is **snapshot semantics** - you can keep references to any version!

### Looking Up Vectors with `get`

```clojure
;; get returns the vector by ID
(get idx3 "doc-1")  ; => #object["[F@..."]  (float array)

;; Convert to sequence to see values
(seq (get idx3 "doc-1"))  ; => (1.0 0.0 0.0)

;; Works like a map
(idx3 "doc-2")  ; => #object[...]
(seq (idx3 "doc-2"))  ; => (0.0 1.0 0.0)

;; Missing keys
(get idx3 "nonexistent")  ; => nil
(get idx3 "nonexistent" :not-found)  ; => :not-found
```

## Level 2: Batch Operations with `into`

When you have many vectors to add, `into` is the way to go:

```clojure
(def documents
  [["doc-4" (float-array [0.5 0.5 0.0])]
   ["doc-5" (float-array [0.1 0.2 0.3])]
   ["doc-6" (float-array [0.4 0.5 0.6])]
   ["doc-7" (float-array [0.7 0.8 0.9])]])

(def idx4 (into idx3 documents))
(count idx4)  ; => 7
```

**Why is `into` fast?** It uses transient operations internally! More on that in Level 4.

## Level 3: Metadata - Store Extra Data

Prox lets you attach metadata to vectors (perfect for Datahike entity IDs!):

```clojure
;; Pass a map with :vector and :metadata
(def idx5 (assoc idx4 "doc-8"
                 {:vector (float-array [1.0 1.0 1.0])
                  :metadata {:entity-id 123
                            :title "My Document"
                            :category "AI"}}))

;; Get the vector
(seq (get idx5 "doc-8"))  ; => (1.0 1.0 1.0)

;; Get the metadata
(prox/get-metadata idx5 "doc-8")
; => {:entity-id 123, :title "My Document", :category "AI"}
```

### Batch with Metadata

```clojure
(def docs-with-metadata
  [["a" {:vector (float-array [1.0 0.0 0.0])
         :metadata {:entity-id 100}}]
   ["b" {:vector (float-array [0.0 1.0 0.0])
         :metadata {:entity-id 101}}]
   ["c" {:vector (float-array [0.0 0.0 1.0])
         :metadata {:entity-id 102}}]])

(def idx6 (into idx docs-with-metadata))
```

## Level 4: Transient for Maximum Performance

For building large indices, use transient explicitly:

```clojure
(def idx7
  (persistent!
    (reduce (fn [t-idx [id vec meta]]
              (assoc! t-idx id {:vector vec :metadata meta}))
            (transient idx)
            [["x" (float-array [1.0 0.0 0.0]) {:type :a}]
             ["y" (float-array [0.0 1.0 0.0]) {:type :b}]
             ["z" (float-array [0.0 0.0 1.0]) {:type :c}]])))
```

**What's happening:**
1. `(transient idx)` - Create mutable builder
2. `(assoc! ...)` - Mutate in place (fast!)
3. `(persistent!)` - Convert back to immutable

**When to use:**
- Building indices with thousands of vectors
- Inner loops where performance matters
- `into` uses this automatically!

## Level 5: Nearest Neighbor Search

**Important**: `get` returns exact matches by ID. For similarity search, use `prox/search`:

```clojure
;; Find vectors similar to this query
(def query (float-array [0.9 0.1 0.0]))

(prox/search idx5 query 3)
; => ({:id "doc-1", :distance 0.141...}
;     {:id "doc-4", :distance 0.714...}
;     {:id "doc-2", :distance 1.414...})
```

Returns maps with `:id` (external ID) and `:distance` (lower = more similar).

### Combining Search with Metadata

```clojure
;; Get full info for search results
(map (fn [result]
       {:id (:id result)
        :distance (:distance result)
        :metadata (prox/get-metadata idx5 (:id result))
        :vector (seq (get idx5 (:id result)))})
     (prox/search idx5 query 2))
```

## Level 6: Snapshot Semantics & Time Travel

Every operation returns a new snapshot. All snapshots are queryable!

```clojure
(def v1 (assoc idx "a" (float-array [1.0 0.0 0.0])))
(def v2 (assoc v1 "b" (float-array [0.0 1.0 0.0])))
(def v3 (assoc v2 "c" (float-array [0.0 0.0 1.0])))

;; All versions are independent
(count v1)  ; => 1
(count v2)  ; => 2
(count v3)  ; => 3

;; Query any version
(prox/search v1 (float-array [1.0 0.0 0.0]) 1)
; => [{:id "a", :distance 0.0}]

(prox/search v3 (float-array [1.0 0.0 0.0]) 3)
; => [{:id "a", :distance 0.0}, {:id "b", :distance 1.414...}, {:id "c", :distance 1.414...}]
```

**Use cases:**
- Compare versions (A/B testing)
- Rollback to previous state
- Concurrent readers (each with their own snapshot)

## Level 7: Durability with `sync!`

For persistent indices (not `:memory`), changes need to be synced:

```clojure
(def persistent-idx
  (prox/create-index {:type :hnsw
                      :dim 3
                      :store-config {:backend :file
                                    :path "/tmp/my-index"
                                    :id (java.util.UUID/randomUUID)}
                      :mmap-dir "/tmp/my-index-mmap"
                      :capacity 1000}))

(def idx-updated
  (into persistent-idx
        [["doc-1" (float-array [1.0 0.0 0.0])]
         ["doc-2" (float-array [0.0 1.0 0.0])]]))

;; Changes are in memory...
(prox/sync! idx-updated)
;; ...now they're on disk!
```

**Without `sync!`**: Changes lost on JVM restart
**With `sync!`**: Durable, can be loaded later

## Level 8: Real Example - Wikipedia Corpus

This is how we build the actual Wikipedia index in this project:

```clojure
;; We have a map of: entity-id → embedding vector
(def embeddings-map
  {122 (float-array (repeat 384 0.1))
   123 (float-array (repeat 384 0.2))
   124 (float-array (repeat 384 0.3))
   ;; ... thousands more
   })

;; Create index
(def wiki-idx
  (prox/create-index {:type :hnsw
                      :dim 384
                      :M 16
                      :ef-construction 200
                      :store-config {:backend :file
                                    :path "data/prox/store"
                                    :id (java.util.UUID/randomUUID)}
                      :mmap-dir "data/prox/mmap"
                      :capacity 100000}))

;; Build with into - clean and fast!
(def wiki-idx-populated
  (into wiki-idx
        (map (fn [[eid embedding]]
               [eid {:vector embedding
                     :metadata {:entity-id eid}}])
             embeddings-map)))

;; Make it durable
(prox/sync! wiki-idx-populated)
```

**That's it!** About 10 lines of code to build a searchable 100k vector index.

## API Quick Reference

### Collection Protocols

| Operation | Example | Description |
|-----------|---------|-------------|
| `assoc` | `(assoc idx id vec)` | Add vector by ID |
| `get` | `(get idx id)` | Retrieve vector by ID |
| `count` | `(count idx)` | Number of vectors |
| `into` | `(into idx entries)` | Batch add |
| `transient` | `(transient idx)` | Mutable builder |
| `assoc!` | `(assoc! t-idx id vec)` | Mutable add |
| `persistent!` | `(persistent! t-idx)` | Convert back to immutable |

### Prox-Specific Operations

| Operation | Example | Description |
|-----------|---------|-------------|
| `prox/search` | `(prox/search idx query k)` | Find k nearest neighbors |
| `prox/get-metadata` | `(prox/get-metadata idx id)` | Get metadata by ID |
| `prox/sync!` | `(prox/sync! idx)` | Write to disk (persistence) |
| `prox/fork` | `(prox/fork idx)` | O(1) copy-on-write fork |
| `prox/close!` | `(prox/close! idx)` | Release resources |

## Key Takeaways

✅ **Feels like Clojure** - Uses standard collection protocols
✅ **Immutable by default** - Every operation returns new index
✅ **Transient for performance** - Explicit control when needed
✅ **Snapshot semantics** - Time travel built-in
✅ **Metadata support** - Perfect for Datahike integration
✅ **Durability when you want it** - `sync!` for persistence

## Next Steps

- Try `demo-prox-protocols.clj` for interactive examples
- See `setup-corpus.clj` for real-world usage with 8k vectors
- Read `docs/datalog-semantic-search-patterns.md` for Datahike integration
