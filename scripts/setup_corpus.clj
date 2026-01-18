#!/usr/bin/env clojure
(ns setup-corpus
  "Setup script for loading Wikipedia corpus into Datahike + Prox.

  Usage:
    clj -M:setup-corpus          # Quick setup with pre-computed embeddings
    clj -M:setup-corpus --full   # Full setup, regenerate embeddings"
  (:require [datahike.api :as d]
            [proximum.core :as prox]
            [libpython-clj2.python :as py]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.java.shell :refer [sh]])
  (:import [java.util.zip GZIPInputStream]
           [java.io InputStreamReader PushbackReader])
  (:gen-class))

;; =============================================================================
;; Configuration
;; =============================================================================

(def CORPUS-DIR "corpus")
(def DB-PATH "data/datahike/store")
(def PROX-STORE-PATH "data/prox/store")
(def PROX-MMAP-DIR "data/prox/mmap")
(def PROX-CAPACITY 1000000)

;; =============================================================================
;; Utilities
;; =============================================================================

(defn read-edn-gz
  "Read EDN data from gzipped file"
  [filepath]
  (println (format "📥 Reading %s..." filepath))
  (with-open [fis (io/input-stream filepath)
              gzis (GZIPInputStream. fis)
              reader (InputStreamReader. gzis "UTF-8")
              pbr (PushbackReader. reader)]
    (let [data (edn/read pbr)
          size-mb (/ (.length (io/file filepath)) 1024.0 1024.0)]
      (println (format "   ✓ Read %.1f MB" size-mb))
      data)))

;; =============================================================================
;; Database Setup
;; =============================================================================

(defn create-database! []
  (println "\n=== Creating Datahike Database ===")
  (println "📍 Path:" DB-PATH)

  (let [config {:store {:backend :file
                        :path DB-PATH
                        :id #uuid "4d2e8c8e-9a1b-4f7c-b3d4-5e6f7a8b9c0d"}
                :schema-flexibility :write
                :keep-history? false}]

    ;; Clean up any existing database or directory
    (when (.exists (io/file DB-PATH))
      (println "🗑️  Removing existing directory...")
      (sh "rm" "-rf" DB-PATH))

    ;; Create parent directory (data/datahike) but not the store directory itself
    (.mkdirs (io/file (.getParent (io/file DB-PATH))))

    (println "🔨 Creating database...")
    (d/create-database config)

    (let [conn (d/connect config)]
      (println "📝 Installing schema...")
      (d/transact conn [{:db/ident :article/title
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one
                         :db/unique :db.unique/identity}
                        {:db/ident :article/page-id
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one}
                        {:db/ident :article/wikitext
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one}
                        {:db/ident :article/category
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/many}
                        {:db/ident :chunk/article
                         :db/valueType :db.type/ref
                         :db/cardinality :db.cardinality/one}
                        {:db/ident :chunk/index
                         :db/valueType :db.type/long
                         :db/cardinality :db.cardinality/one}
                        {:db/ident :chunk/text
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one}
                        {:db/ident :chunk/tokens
                         :db/valueType :db.type/long
                         :db/cardinality :db.cardinality/one}])

      (println "✅ Database created")
      conn)))

(defn load-articles! [conn]
  (println "\n=== Loading Articles ===")
  (let [articles (read-edn-gz (str CORPUS-DIR "/articles.edn.gz"))]
    (println (format "💾 Transacting %d articles..." (count articles)))
    (let [start (System/currentTimeMillis)]
      (d/transact conn articles)
      (let [elapsed (/ (- (System/currentTimeMillis) start) 1000.0)]
        (println (format "✅ Loaded %d articles in %.1f seconds" (count articles) elapsed))))))

(defn load-chunks! [conn]
  (println "\n=== Loading Chunks ===")
  (let [chunks (read-edn-gz (str CORPUS-DIR "/chunks.edn.gz"))]
    (println (format "💾 Transacting %d chunks..." (count chunks)))
    (let [start (System/currentTimeMillis)]
      (d/transact conn chunks)
      (let [elapsed (/ (- (System/currentTimeMillis) start) 1000.0)]
        (println (format "✅ Loaded %d chunks in %.1f seconds" (count chunks) elapsed))))))

;; =============================================================================
;; Prox Index
;; =============================================================================

(defn create-prox-index []
  (println "\n=== Creating Prox Vector Index ===")
  (println "📍 Store:" PROX-STORE-PATH)
  (println "📍 Mmap:" PROX-MMAP-DIR)

  ;; Clean up old index directories if they exist
  (when (.exists (io/file "data/prox"))
    (println "🗑️  Removing old index...")
    (sh "rm" "-rf" "data/prox"))

  ;; Create parent directory for mmap (Prox will create its own subdirectories)
  (.mkdirs (io/file "data/prox/mmap"))

  (let [idx (prox/create-index {:type :hnsw
                                :dim 384
                                :M 16
                                :ef-construction 200
                                :store-config {:backend :file
                                              :path PROX-STORE-PATH
                                              :id #uuid "7f3a9c2d-5e4b-4a8f-9c1d-2e3f4a5b6c7d"}
                                :mmap-dir PROX-MMAP-DIR
                                :capacity PROX-CAPACITY})]
    (println "✅ Prox index created")
    idx))

(defn load-embeddings-into-prox [idx]
  (println "\n=== Loading Pre-computed Embeddings ===")
  (let [embeddings-map (read-edn-gz (str CORPUS-DIR "/embeddings.edn.gz"))
        _ (println (format "🔄 Populating index with %d embeddings..." (count embeddings-map)))

        start (System/currentTimeMillis)
        entries (mapv (fn [[eid embedding-vec]]
                        [eid {:vector (float-array embedding-vec)
                              :metadata {:entity-id eid}}])
                      embeddings-map)

        idx2 (into idx entries)
        elapsed (/ (- (System/currentTimeMillis) start) 1000.0)
        rate (/ (count embeddings-map) elapsed)]

    (println (format "✅ Inserted %d vectors in %.1f seconds (%.0f/sec)"
                     (count embeddings-map) elapsed rate))

    (println "💾 Syncing to storage...")
    (prox/sync! idx2)
    (println "✅ Index persisted")

    idx2))

(defn init-python! []
  (println "\n=== Initializing Python ===")
  (py/initialize! :python-executable ".venv/bin/python3")
  (let [sys (py/import-module "sys")
        executable (py/get-attr sys "executable")]
    (println "🐍 Python:" executable))
  (println "✅ Python initialized"))

(defn create-embedder []
  (println "🤖 Loading FastEmbed model (BAAI/bge-small-en-v1.5)...")
  (let [fe (py/import-module "fastembed")
        TextEmbedding (py/get-attr fe "TextEmbedding")
        embedder (TextEmbedding :model_name "BAAI/bge-small-en-v1.5")]
    (println "✅ Model loaded (384 dimensions)")
    embedder))

(defn search-similar-chunks
  "Search for chunks by text query, returns entity IDs"
  [prox-idx embedder query-text k]
  (let [query-embedding (first (mapv #(float-array (py/->jvm %))
                                    (py/call-attr embedder "embed" [query-text])))
        results (prox/search prox-idx query-embedding k)]
    (mapv :id results)))

(defn test-search [conn prox-idx embedder]
  (println "\n=== Testing Semantic Search ===")
  (let [query "What is lazy evaluation in functional programming?"
        _ (println "🔍 Query:" query)
        eids (search-similar-chunks prox-idx embedder query 5)
        results (d/q '[:find ?title ?text
                       :in $ [?eid ...]
                       :where
                       [?eid :chunk/text ?text]
                       [?eid :chunk/article ?article]
                       [?article :article/title ?title]]
                     @conn
                     eids)]

    (println "\n📊 Top 5 results:")
    (doseq [[idx [title text]] (map-indexed vector results)]
      (println (format "\n%d. %s" (inc idx) title))
      (println (format "   %s..." (subs text 0 (min 120 (count text))))))

    (println "\n✅ Search test complete")))

;; =============================================================================
;; Main Workflows
;; =============================================================================

(defn quick-setup!
  "Quick setup using pre-computed embeddings (~2 minutes)"
  []
  (let [start-time (System/currentTimeMillis)]

    ;; 1. Create database
    (def conn (create-database!))

    ;; 2. Load articles and chunks
    (load-articles! conn)
    (load-chunks! conn)

    ;; 3. Create Prox index and load embeddings
    (def prox-idx (create-prox-index))
    (def prox-idx (load-embeddings-into-prox prox-idx))

    ;; 4. Initialize Python for queries
    (init-python!)
    (def embedder (create-embedder))

    ;; 5. Test search
    (test-search conn prox-idx embedder)

    (let [total-time (/ (- (System/currentTimeMillis) start-time) 1000.0)]
      (println "\n╔════════════════════════════════════════════╗")
      (println "║  ✅ SETUP COMPLETE                         ║")
      (println "╚════════════════════════════════════════════╝")
      (println (format "⏱️  Total time: %.1f seconds" total-time))
      (println "\n📚 Database ready for queries!")
      (println "📝 See docs/datalog-semantic-search-patterns.md for query examples")

      {:conn conn
       :prox-idx prox-idx
       :embedder embedder
       :time total-time})))

(defn -main
  "Main entry point for command-line execution"
  [& args]
  (try
    (println "\n╔════════════════════════════════════════════╗")
    (println "║  WIKIPEDIA CORPUS SETUP                    ║")
    (println "╚════════════════════════════════════════════╝\n")

    ;; Check if corpus files exist
    (when-not (.exists (io/file CORPUS-DIR))
      (println "❌ Error: corpus directory not found!")
      (println "   Please download and extract the corpus first:")
      (println "   wget <corpus-url> && tar -xzf corpus.tar.gz")
      (System/exit 1))

    ;; Run setup
    (quick-setup!)

    (println "\n✅ Setup complete!")
    (println "\n💡 To query the database, start a REPL:")
    (println "   clj -M:repl")
    (println "\nExample query:")
    (println "  (require '[datahike.api :as d])")
    (println "  (d/q '[:find ?title :where [?e :article/title ?title]] @conn)")

    (System/exit 0)

    (catch Exception e
      (println "\n❌ Setup failed:")
      (println (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))
