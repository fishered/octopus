package com.accuenergy.octopus.mgmt.application.catalog;

public final class CatalogAccessDeniedException extends RuntimeException {
    public CatalogAccessDeniedException() { super("Catalog access denied"); }
}
