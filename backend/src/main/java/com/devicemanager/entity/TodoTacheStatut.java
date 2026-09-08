package com.devicemanager.entity;

/**
 * Cycle de vie d'une tâche « À faire ».
 */
public enum TodoTacheStatut {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    public boolean isOpen() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
