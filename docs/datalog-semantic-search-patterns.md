# Datalog + Semantic Search Integration Patterns

## Overview

These patterns demonstrate how to seamlessly integrate Prox vector search with Datahike Datalog queries by calling the search function directly within the `:where` clause using bound logic variables.

## Pattern 1: Two-Step Approach (Basic)

Fetch entity IDs first, then query:

```clojure
(let [eids (search-similar-chunks "lazy evaluation" 5)]
  (d/q '[:find ?title ?chunk-idx ?text
         :in $ [?eid ...]
         :where
         [?eid :chunk/index ?chunk-idx]
         [?eid :chunk/text ?text]
         [?eid :chunk/article ?article-ref]
         [?article-ref :article/title ?title]]
       @conn
       eids))
```

**Pros**: Simple, explicit
**Cons**: Two separate steps, can't use Datalog bindings as search inputs

## Pattern 2: Direct Function Call (Recommended)

Pass search function as input, call directly in `:where` clause:

```clojure
(d/q '[:find ?title ?chunk-idx ?text
       :in $ ?search-fn ?query ?k
       :where
       [(?search-fn ?query ?k) [?eid ...]]
       [?eid :chunk/index ?chunk-idx]
       [?eid :chunk/text ?text]
       [?eid :chunk/article ?article-ref]
       [?article-ref :article/title ?title]]
     @conn
     search-similar-chunks
     "lazy evaluation"
     5)
```

**Key syntax**: `[(?search-fn ?query ?k) [?eid ...]]` invokes the function and binds results to `?eid`

**Pros**: Single query, can parameterize search
**Cons**: Still uses literal query string

## Pattern 3: Combining with Datalog Filters

Search semantically, then filter by Datahike attributes:

```clojure
;; Only chunks later in their articles
(d/q '[:find ?title ?idx ?text
       :in $ ?search-fn ?query
       :where
       [(?search-fn ?query 10) [?eid ...]]
       [?eid :chunk/index ?idx]
       [?eid :chunk/text ?text]
       [?eid :chunk/article ?article-ref]
       [?article-ref :article/title ?title]
       [(> ?idx 2)]]  ; Datalog filter
     @conn
     search-similar-chunks
     "type systems and static analysis")
```

**Pros**: Combines semantic + structural filters
**Cons**: Post-hoc filtering can be less efficient

## Pattern 4: Search with Bound Variables (Advanced)

**The coolest pattern**: Use previously bound logic variables as search input.

### Example: Cross-Category Semantic Discovery

Find chunks semantically similar to chunks from a specific category, but from OTHER articles:

```clojure
(d/q '[:find ?seed-title ?seed-idx ?result-title
       :in $ ?search-fn ?category
       :where
       ;; Bind a seed chunk from the category
       [?article :article/category ?category]
       [?article :article/title ?seed-title]
       [?seed-chunk :chunk/article ?article]
       [?seed-chunk :chunk/index ?seed-idx]
       [?seed-chunk :chunk/text ?seed-text]
       [(< ?seed-idx 2)]  ; Use early chunks only

       ;; Search for similar chunks using the bound text
       [(?search-fn ?seed-text 3) [?eid ...]]

       ;; Get results from different articles
       [?eid :chunk/article ?result-article]
       [?result-article :article/title ?result-title]
       [(not= ?result-article ?article)]]  ; Exclude source article
     @conn
     search-similar-chunks
     "Functional programming")
```

**Example results**:
- "Partial application" (chunk 0) → "Side effect (computer science)"
- "Pattern matching" (chunk 1) → "SNOBOL"
- "Category:Combinatory logic" (chunk 0) → "Category:Lambda calculus"

**Why this is powerful**:
1. **No post-hoc filtering inefficiency**: Search happens AFTER binding seed chunk text
2. **Uses bound variables as search input**: `?seed-text` is bound by Datalog, then used in search
3. **Discovers cross-article semantic connections**: Reveals implicit relationships
4. **Pure Datalog syntax**: No external loops or coordination needed

### Variations

**Find similar chunks, stay within category**:
```clojure
[:where
 [?article :article/category ?category]
 [?seed-chunk :chunk/article ?article]
 [?seed-chunk :chunk/text ?seed-text]
 [(?search-fn ?seed-text 5) [?eid ...]]
 [?eid :chunk/article ?result-article]
 [?result-article :article/category ?category]  ; Same category
 [(not= ?result-article ?article)]]  ; Different article
```

**Chain semantic searches**:
```clojure
[:where
 ;; First semantic hop
 [(?search-fn "monads" 3) [?hop1 ...]]
 [?hop1 :chunk/text ?hop1-text]

 ;; Second semantic hop using first hop's text
 [(?search-fn ?hop1-text 3) [?hop2 ...]]
 [?hop2 :chunk/text ?result-text]]
```

**Filter by chunk properties before search**:
```clojure
[:where
 [?seed-chunk :chunk/tokens ?tokens]
 [(> ?tokens 400)]  ; Only use substantial chunks
 [?seed-chunk :chunk/text ?seed-text]
 [(?search-fn ?seed-text 10) [?eid ...]]]
```

## Key Insights

1. **Interleaving is the key**: `[(?search-fn ?bound-var k) [?eid ...]]` enables true Datalog + vector search integration

2. **Bind before search**: The search function can use ANY previously bound logic variable as input

3. **No special support needed**: This works out of the box with Datahike's function call syntax

4. **Semantic graph traversal**: You can "walk" the semantic space using Datalog's logic programming

## Performance Considerations

- **Pattern 4 efficiency**: Search happens AFTER binding variables, so you only search once per distinct binding
- **Multiple bindings**: If seed chunk query matches N chunks, search runs N times (use filters to reduce N)
- **Index selectivity**: Filter by category/attributes BEFORE semantic search when possible
- **Search k parameter**: Keep k small (3-10) when using bound variables to avoid combinatorial explosion

## Implementation Note

The `search-similar-chunks` helper function:

```clojure
(defn search-similar-chunks [query-text k]
  (let [query-embedding (first (mapv #(float-array (py/->jvm %))
                                    (py/call-attr embedder "embed" [query-text])))
        results (prox/search prox-idx query-embedding k)]
    (mapv :id results)))
```

Takes text input (string), returns entity IDs (vector of longs). Perfect for Datalog integration.
