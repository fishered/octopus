package com.accuenergy.octopus.ca.application;

public interface ProofOfPossessionPort {
    boolean verify(byte[] certificateSigningRequest);
}

