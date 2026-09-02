package com.accuenergy.octopus.mgmt.application.iam;

public final class IamAccessDeniedException extends RuntimeException {
    public IamAccessDeniedException() { super("IAM administration access denied"); }
}
