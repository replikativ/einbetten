#!/usr/bin/env bash
#
# Setup Wikipedia semantic search demo
#
# Usage: ./setup.sh
#
# Requirements:
# - corpus/ directory with articles.edn.gz, chunks.edn.gz, embeddings.edn.gz
# - Python venv with fastembed installed (.venv/bin/python3)
# - ~4GB RAM
# - ~2 minutes to complete
#

set -e

echo "🚀 Setting up Wikipedia semantic search demo..."
echo ""

# Check if corpus exists
if [ ! -d "corpus" ]; then
    echo "❌ Error: corpus/ directory not found!"
    echo ""
    echo "Please download and extract the corpus first:"
    echo "  wget <corpus-url>/corpus.tar.gz"
    echo "  tar -xzf corpus.tar.gz"
    echo ""
    exit 1
fi

# Check if Python venv exists
if [ ! -f ".venv/bin/python3" ]; then
    echo "⚠️  Warning: Python venv not found at .venv/"
    echo ""
    echo "Creating venv and installing fastembed..."
    python3 -m venv .venv
    .venv/bin/pip install -q fastembed
    echo "✅ Python venv created"
    echo ""
fi

echo "This will:"
echo "  - Create Datahike database (LMDB backend)"
echo "  - Load articles and chunks"
echo "  - Create Proximum vector index"
echo "  - Load pre-computed embeddings"
echo "  - Test semantic search"
echo ""
echo "⏱️  Estimated time: 2 minutes"
echo ""

# Run setup
clj -M:setup

echo ""
echo "✅ Setup complete!"
echo ""
echo "📚 You can now query the database. Try:"
echo "   clj -M:repl"
echo ""
echo "See docs/datalog-semantic-search-patterns.md for example queries."
