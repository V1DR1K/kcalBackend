package com.scalegrams.common;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchTextNormalizer {
    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
