package com.devicemanager.tenancy;

public final class AtelierContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private AtelierContext() {
    }

    public static void set(Long atelierId) {
        CURRENT.set(atelierId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("Aucun atelier sélectionné");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
