package com.accuenergy.octopus.mgmt.infrastructure.security;

import java.util.UUID;

/** Implementations atomically accept only counters greater than the last accepted value. */
public interface TotpCounterStore {
    boolean tryAdvance(UUID factorId, long counter);
}
