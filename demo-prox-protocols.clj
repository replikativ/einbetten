#!/usr/bin/env clojure
(ns demo-prox-protocols
  "Interactive demo of Prox's Clojure collection protocols.

  Showcases how Prox implements standard Clojure protocols,
  making it feel like working with native Clojure data structures.

  Load this in a REPL and follow along!"
  (:require [proximum.core :as prox]
            [clojure.java.io :as io]))

(comment
  ;; =============================================================================
  ;; Part 1: Creating an Index - The Java-style API
  ;; =============================================================================

  "Let's create a simple in-memory index for playing around"

  (def idx (prox/create-index {:type :hnsw
                                :dim 3            ; Small for easy visualization
                                :M 8
                                :ef-construction 50
                                :store-config {:backend :memory
                                              :id (java.util.UUID/randomUUID)}
                                :capacity 100}))

  ;; Empty index
  (count idx)  ; => 0


  ;; =============================================================================
  ;; Part 2: Clojure Collection Protocols - It's Just a Map!
  ;; =============================================================================

  "Prox implements IPersistentMap, so you can use it like a regular Clojure map!"

  ;; assoc works! (like conj for maps)
  (def idx2 (assoc idx "doc-1" (float-array [1.0 0.0 0.0])))
  (count idx2)  ; => 1

  ;; But idx is unchanged! (Persistent = immutable)
  (count idx)   ; => 0  (original unchanged)

  "This is SNAPSHOT SEMANTICS - each version is independent!"

  ;; Add more vectors
  (def idx3 (-> idx2
                (assoc "doc-2" (float-array [0.0 1.0 0.0]))
                (assoc "doc-3" (float-array [0.0 0.0 1.0]))
                (assoc "doc-4" (float-array [0.5 0.5 0.0]))))

  (count idx3)  ; => 4
  (count idx2)  ; => 1  (still!)


  ;; =============================================================================
  ;; Part 3: Lookup - Get Returns Vectors (Not Search Results!)
  ;; =============================================================================

  "get returns the stored vector by external ID"

  (get idx3 "doc-1")  ; => #object[..."[F@..."]  (float array)
  (seq (get idx3 "doc-1"))  ; => (1.0 0.0 0.0)

  (get idx3 "nonexistent")  ; => nil
  (get idx3 "nonexistent" :not-found)  ; => :not-found

  "ILookup protocol works!"
  (idx3 "doc-2")  ; => #object[...]
  (seq (idx3 "doc-2"))  ; => (0.0 1.0 0.0)


  ;; =============================================================================
  ;; Part 4: Metadata - Store Arbitrary Data with Vectors
  ;; =============================================================================

  "assoc can take {:vector :metadata} for rich data"

  (def idx4 (-> idx3
                (assoc "doc-5" {:vector (float-array [1.0 1.0 1.0])
                                :metadata {:entity-id 123
                                          :title "My Document"
                                          :timestamp 1234567890}})))

  ;; Get just the vector
  (seq (get idx4 "doc-5"))  ; => (1.0 1.0 1.0)

  ;; Get the metadata
  (prox/get-metadata idx4 "doc-5")
  ; => {:entity-id 123, :title "My Document", :timestamp 1234567890}


  ;; =============================================================================
  ;; Part 5: Batch Operations with 'into' - Uses Transient Internally!
  ;; =============================================================================

  "'into' is super fast because it uses transient operations under the hood"

  (def more-docs
    [["doc-6" (float-array [0.1 0.2 0.3])]
     ["doc-7" (float-array [0.4 0.5 0.6])]
     ["doc-8" (float-array [0.7 0.8 0.9])]
     ["doc-9" (float-array [0.3 0.6 0.9])]])

  ;; Batch insert with into
  (def idx5 (into idx4 more-docs))
  (count idx5)  ; => 9

  "into automatically uses transient for performance!"


  ;; =============================================================================
  ;; Part 6: Transient Operations - Explicit Performance Control
  ;; =============================================================================

  "For building large indices, use transient explicitly"

  (def idx6
    (persistent!
      (reduce (fn [t-idx [id vec]]
                (assoc! t-idx id vec))
              (transient idx)
              [["a" (float-array [1.0 0.0 0.0])]
               ["b" (float-array [0.0 1.0 0.0])]
               ["c" (float-array [0.0 0.0 1.0])]
               ["d" (float-array [0.5 0.5 0.5])]])))

  (count idx6)  ; => 4

  "Transient operations (assoc!, conj!) are mutable for speed,
   then persistent! converts back to immutable"

  ;; With metadata too!
  (def idx7
    (persistent!
      (-> (transient idx)
          (assoc! "x" {:vector (float-array [1.0 0.5 0.0])
                      :metadata {:type :test :value 42}}))))

  (prox/get-metadata idx7 "x")  ; => {:type :test, :value 42}


  ;; =============================================================================
  ;; Part 7: Nearest Neighbor Search - The Special Protocol
  ;; =============================================================================

  "Search is NOT done with 'get' - it has its own protocol!"

  ;; prox/search returns nearest neighbors
  (def query (float-array [0.9 0.1 0.0]))  ; Close to doc-1

  (prox/search idx5 query 3)
  ; => ({:id "doc-1", :distance 0.14142...}
  ;     {:id "doc-4", :distance 0.71414...}
  ;     {:id "doc-2", :distance 1.41421...})

  "Returns maps with :id (external-id) and :distance"

  ;; Get top result's vector
  (let [top-result (first (prox/search idx5 query 1))
        top-id (:id top-result)]
    (seq (get idx5 top-id)))
  ; => (1.0 0.0 0.0)  ; doc-1, as expected!


  ;; =============================================================================
  ;; Part 8: Snapshot Semantics - Time Travel!
  ;; =============================================================================

  "Each version is completely independent - true persistence"

  (def v1 (assoc idx "a" (float-array [1.0 0.0 0.0])))
  (def v2 (assoc v1 "b" (float-array [0.0 1.0 0.0])))
  (def v3 (assoc v2 "c" (float-array [0.0 0.0 1.0])))

  (count v1)  ; => 1
  (count v2)  ; => 2
  (count v3)  ; => 3

  ;; All versions are queryable!
  (prox/search v1 (float-array [1.0 0.0 0.0]) 1)
  ; => ({:id "a", :distance 0.0})

  (prox/search v3 (float-array [1.0 0.0 0.0]) 3)
  ; => ({:id "a", :distance 0.0}
  ;     {:id "b", :distance 1.414...}
  ;     {:id "c", :distance 1.414...})

  "You can keep references to any version and query them independently!"


  ;; =============================================================================
  ;; Part 9: Durability - The sync! Step
  ;; =============================================================================

  "For persistent indices (non-memory), sync! writes to disk"

  (def persistent-idx
    (prox/create-index {:type :hnsw
                        :dim 3
                        :M 8
                        :ef-construction 50
                        :store-config {:backend :file
                                      :path "/tmp/prox-demo"
                                      :id (java.util.UUID/randomUUID)}
                        :mmap-dir "/tmp/prox-demo-mmap"
                        :capacity 100}))

  (def persistent-idx2
    (-> persistent-idx
        (assoc "doc-1" (float-array [1.0 0.0 0.0]))
        (assoc "doc-2" (float-array [0.0 1.0 0.0]))))

  "Changes are in memory until you sync!"
  (prox/sync! persistent-idx2)
  ; => <commits to disk>

  "Without sync!, changes are lost on JVM exit.
   With sync!, they're durable and can be loaded later."

  ;; Clean up
  (prox/close! persistent-idx2)
  (clojure.java.shell/sh "rm" "-rf" "/tmp/prox-demo" "/tmp/prox-demo-mmap")


  ;; =============================================================================
  ;; Part 10: Comparison - Collection Protocols vs Java API
  ;; =============================================================================

  "Two ways to build the same index:"

  ;; Way 1: Collection protocols (idiomatic Clojure)
  (def idx-clojure
    (into idx
          [["a" (float-array [1.0 0.0 0.0])]
           ["b" (float-array [0.0 1.0 0.0])]
           ["c" (float-array [0.0 0.0 1.0])]]))

  ;; Way 2: Java-style API
  (def idx-java
    (-> idx
        (prox/insert (float-array [1.0 0.0 0.0]) "a" {})
        (prox/insert (float-array [0.0 1.0 0.0]) "b" {})
        (prox/insert (float-array [0.0 0.0 1.0]) "c" {})))

  ;; Or batch:
  (def idx-java-batch
    (prox/insert-batch idx
                       [(float-array [1.0 0.0 0.0])
                        (float-array [0.0 1.0 0.0])
                        (float-array [0.0 0.0 1.0])]
                       {:external-ids ["a" "b" "c"]}))

  "All three approaches work! Use what feels most natural."

  (count idx-clojure)      ; => 3
  (count idx-java)         ; => 3
  (count idx-java-batch)   ; => 3


  ;; =============================================================================
  ;; Part 11: Real Example - Building from Embeddings Map
  ;; =============================================================================

  "This is how we build the Wikipedia index!"

  (def embeddings-map
    {122 (float-array (repeatedly 384 rand))
     123 (float-array (repeatedly 384 rand))
     124 (float-array (repeatedly 384 rand))
     125 (float-array (repeatedly 384 rand))})

  ;; Create index
  (def wiki-idx
    (prox/create-index {:type :hnsw
                        :dim 384
                        :M 16
                        :ef-construction 200
                        :store-config {:backend :memory
                                      :id (java.util.UUID/randomUUID)}
                        :capacity 10000}))

  ;; Build with into - super clean!
  (def wiki-idx2
    (into wiki-idx
          (map (fn [[eid embedding]]
                 [eid {:vector embedding
                       :metadata {:entity-id eid}}])
               embeddings-map)))

  (count wiki-idx2)  ; => 4

  ;; Query
  (def query-vec (float-array (repeatedly 384 rand)))
  (prox/search wiki-idx2 query-vec 2)
  ; => ({:id 123, :distance 8.234...} {:id 122, :distance 8.456...})

  ;; Get metadata for results
  (map #(prox/get-metadata wiki-idx2 (:id %))
       (prox/search wiki-idx2 query-vec 2))
  ; => ({:entity-id 123} {:entity-id 122})

  "Clean, functional, idiomatic Clojure!"


  ;; =============================================================================
  ;; Summary
  ;; =============================================================================

  "Prox gives you:

   ✅ Standard Clojure collection protocols (assoc, get, into, transient)
   ✅ Immutable, persistent data structure (snapshot semantics)
   ✅ Dedicated NearestNeighborSearch protocol (search returns neighbors, not exact matches)
   ✅ Optional durability with sync!
   ✅ Time travel - keep references to any version
   ✅ Zero-copy forks with prox/fork
   ✅ Both Clojure-idiomatic and Java-friendly APIs

   It's a vector database that FEELS like Clojure!"

  ) ;; end comment
