package com.tk.wordagents.game;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Word meanings as GloVe vectors (glove.6B, 50 dimensions), so the dealer can tell
 * how close two board words are. {@code resources/vectors/glove-50d.bin.gz} keeps the
 * 40k most common words plus every built-in pack word, one signed byte per value;
 * {@code server/scripts/build-word-vectors.py} rebuilds it. Cards with no vector fall back
 * to the pack's sub-themes.
 */
final class WordVectors {

    private static final String RESOURCE = "/vectors/glove-50d.bin.gz";
    private static final WordVectors INSTANCE = load();

    private final Map<String, byte[]> vectors;

    private WordVectors(Map<String, byte[]> vectors) {
        this.vectors = vectors;
    }

    static WordVectors get() {
        return INSTANCE;
    }

    /**
     * A unit vector for a card word. A word of several parts (ICE CREAM) is the mean
     * of its parts, unless the whole is known (X-RAY). Empty if any part is unknown.
     */
    Optional<float[]> vector(String word) {
        String key = word.toLowerCase(Locale.ROOT);
        byte[] whole = vectors.get(key.replace(' ', '-'));
        if (whole != null) return Optional.of(unit(add(new float[whole.length], whole)));
        String[] parts = key.split("[ -]+");
        float[] sum = null;
        for (String part : parts) {
            byte[] vector = vectors.get(part);
            if (vector == null) return Optional.empty();
            sum = add(sum == null ? new float[vector.length] : sum, vector);
        }
        return sum == null ? Optional.empty() : Optional.of(unit(sum));
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }

    private static float[] add(float[] sum, byte[] vector) {
        for (int i = 0; i < sum.length; i++) sum[i] += vector[i];
        return sum;
    }

    private static float[] unit(float[] vector) {
        double length = Math.sqrt(cosine(vector, vector));
        if (length == 0) return vector;
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) length;
        return vector;
    }

    private static WordVectors load() {
        try (InputStream raw = WordVectors.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("Missing word vectors " + RESOURCE);
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            int count = in.readInt();
            int dimensions = in.readInt();
            Map<String, byte[]> vectors = HashMap.newHashMap(count);
            for (int i = 0; i < count; i++) {
                String word = in.readUTF();
                byte[] vector = new byte[dimensions];
                in.readFully(vector);
                vectors.put(word, vector);
            }
            return new WordVectors(Map.copyOf(vectors));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
