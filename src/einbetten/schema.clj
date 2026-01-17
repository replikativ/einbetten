(ns einbetten.schema
  "Complete Wikipedia article schema for Datahike")

(def article-schema
  "Complete schema for Wikipedia articles with all metadata fields.

  Fields extracted from Wikipedia XML dumps:
  - Core identity and metadata (page-id, title, namespace, redirect)
  - Categories (multi-valued, AVET indexed)
  - Revision metadata (revision-id, parent-revision-id, timestamp)
  - Contributor info (name, id, IP)
  - Edit metadata (comment, minor-edit flag)
  - Content (byte-size, wikitext, SHA1)"

  [;; ========================================
   ;; CORE IDENTITY & METADATA
   ;; ========================================

   {:db/ident :article/page-id
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Wikipedia page ID (unique identifier)"}

   {:db/ident :article/title
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Article title"}

   {:db/ident :article/namespace
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Wikipedia namespace (0=main, 1=talk, etc.)"}

   {:db/ident :article/redirect
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Redirect target title if redirect page"}

   ;; ========================================
   ;; CATEGORIES
   ;; ========================================

   {:db/ident :article/category
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/index true
    :db/doc "Article categories from [[Category:...]] (AVET indexed for fast lookups)"}

   ;; ========================================
   ;; REVISION METADATA
   ;; ========================================

   {:db/ident :article/revision-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Current revision ID"}

   {:db/ident :article/parent-revision-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Parent revision ID"}

   {:db/ident :article/timestamp
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Last edit timestamp"}

   ;; ========================================
   ;; CONTRIBUTOR INFO
   ;; ========================================

   {:db/ident :article/contributor-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Last contributor username"}

   {:db/ident :article/contributor-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Last contributor user ID"}

   {:db/ident :article/contributor-ip
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "IP address for anonymous edits"}

   ;; ========================================
   ;; EDIT METADATA
   ;; ========================================

   {:db/ident :article/edit-comment
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Edit summary/comment"}

   {:db/ident :article/minor-edit
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether this was marked as a minor edit"}

   ;; ========================================
   ;; CONTENT METADATA
   ;; ========================================

   {:db/ident :article/byte-size
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Article size in bytes"}

   {:db/ident :article/sha1
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "SHA1 hash of content"}

   {:db/ident :article/wikitext
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Raw wikitext content"}])

(def chunk-schema
  "Schema for text chunks (Phase 2).

  Each chunk is a segment of an article's wikitext, suitable for embedding.
  Chunks are linked to their parent article via :chunk/article reference."

  [{:db/ident :chunk/text
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Chunk text (max 512 tokens)"}

   {:db/ident :chunk/article
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/isComponent true
    :db/doc "Parent article reference"}

   {:db/ident :chunk/position
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Chunk position in article (0-based)"}

   {:db/ident :chunk/section-title
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Wikipedia section title"}

   {:db/ident :chunk/start-offset
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Character start offset in wikitext"}

   {:db/ident :chunk/end-offset
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Character end offset in wikitext"}

   {:db/ident :chunk/token-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Token count (BGE tokenizer)"}

   {:db/ident :chunk/has-embedding
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Flag indicating if embedding has been generated"}])

(def complete-schema
  "Complete schema including both articles and chunks"
  (concat article-schema chunk-schema))
