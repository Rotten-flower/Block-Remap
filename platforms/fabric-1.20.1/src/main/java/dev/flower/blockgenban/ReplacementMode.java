package dev.flower.blockgenban;

import java.io.IOException;

public enum ReplacementMode {
    GENERATION_ONLY("generation_only"),
    GLOBAL("global");

    private final String id;

    ReplacementMode(String id) { this.id=id; }
    public String id() { return id; }

    public static ReplacementMode parse(String value) throws IOException {
        for(ReplacementMode mode:values())if(mode.id.equals(value))return mode;
        throw new IOException("replacementMode must be 'generation_only' or 'global': "+value);
    }
}
