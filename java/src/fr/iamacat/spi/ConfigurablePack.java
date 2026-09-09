package fr.iamacat.spi;

import java.util.Map;

/**
 * B2 load-time contract for packs needing operator configuration (content
 * file paths, ...). The reflective loader instantiates packs with a public
 * no-arg constructor, then calls {@link #configure} when the pack asks for
 * it — never the reverse. Keys and validation belong to the pack, with
 * pack-named refusals. Java 8, zero deps.
 */
public interface ConfigurablePack extends ContentPack {
    /**
     * Receives operator {@code k=v} args (never null). Missing or malformed
     * args are refused loudly, never defaulted.
     */
    void configure(Map<String, String> args);
}
