(ns einbetten.chunking
  "Text chunking utilities for Wikipedia articles"
  (:require [clojure.string :as str]))

(defn basic-clean-wikitext
  "Basic cleanup of wikitext - removes templates, links, refs.
  Not perfect but good enough for initial testing."
  [text]
  (when text
    (-> text
        ;; Remove templates {{...}}
        (str/replace #"\{\{[^}]*\}\}" "")
        ;; Simplify links [[text|display]] -> display
        (str/replace #"\[\[([^|\]]+)\|([^\]]+)\]\]" "$2")
        ;; Simplify simple links [[text]] -> text
        (str/replace #"\[\[([^\]]+)\]\]" "$1")
        ;; Remove refs <ref>...</ref>
        (str/replace #"<ref[^>]*>.*?</ref>" "")
        ;; Remove HTML comments
        (str/replace #"<!--.*?-->" "")
        ;; Remove file/image links
        (str/replace #"\[\[File:.*?\]\]" "")
        (str/replace #"\[\[Image:.*?\]\]" "")
        ;; Clean up multiple newlines
        (str/replace #"\n\n+" "\n\n")
        ;; Clean up multiple spaces
        (str/replace #" +" " ")
        ;; Trim
        str/trim)))

(defn estimate-tokens
  "Rough token count estimation based on word count"
  [text]
  (when text
    (let [words (count (str/split text #"\s+"))]
      (long (* words 1.3)))))

(defn chunk-text
  "Split text into chunks of approximately target-size tokens.
  Uses paragraph-based chunking with size targets."
  [text target-size]
  (when text
    (let [paragraphs (str/split text #"\n\n+")
          chunks (atom [])
          current-chunk (atom [])]

      (doseq [para paragraphs]
        (let [para-tokens (estimate-tokens para)
              current-tokens (estimate-tokens (str/join "\n\n" @current-chunk))]

          (cond
            ;; Current chunk is empty, add paragraph
            (empty? @current-chunk)
            (swap! current-chunk conj para)

            ;; Adding this paragraph would exceed target
            (> (+ current-tokens para-tokens) target-size)
            (do
              ;; Save current chunk
              (swap! chunks conj (str/join "\n\n" @current-chunk))
              ;; Start new chunk with this paragraph
              (reset! current-chunk [para]))

            ;; Add to current chunk
            :else
            (swap! current-chunk conj para))))

      ;; Don't forget the last chunk
      (when (seq @current-chunk)
        (swap! chunks conj (str/join "\n\n" @current-chunk)))

      @chunks)))
