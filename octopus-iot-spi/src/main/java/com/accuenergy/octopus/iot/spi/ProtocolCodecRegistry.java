package com.accuenergy.octopus.iot.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable codec registry assembled at startup. */
public final class ProtocolCodecRegistry {
    private final Map<String, ProtocolCodec> codecs;

    public ProtocolCodecRegistry(List<? extends ProtocolCodec> codecs) {
        Objects.requireNonNull(codecs, "codecs");
        this.codecs = codecs.stream().collect(Collectors.toUnmodifiableMap(
                codec -> {
                    ProtocolCodec value = Objects.requireNonNull(codec, "codec");
                    if (value.codecId() == null || !value.codecId().matches("[a-z][a-z0-9._-]{0,63}")) {
                        throw new IllegalArgumentException("codecId is invalid");
                    }
                    return value.codecId();
                }, Function.identity(), (left, right) -> {
                    throw new IllegalArgumentException("Duplicate IoT codec: " + left.codecId());
                }));
    }

    public ProtocolCodec require(String codecId) {
        ProtocolCodec codec = codecs.get(codecId);
        if (codec == null) throw new IllegalArgumentException("IoT codec is not installed: " + codecId);
        return codec;
    }

    public List<ProtocolCodec> all() { return List.copyOf(codecs.values()); }
}
