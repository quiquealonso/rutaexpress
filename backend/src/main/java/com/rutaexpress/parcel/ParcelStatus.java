package com.rutaexpress.parcel;

import java.util.EnumSet;
import java.util.Set;

public enum ParcelStatus {
    REGISTERED, LOADED, OUT_FOR_DELIVERY, DELIVERED, FAILED_ATTEMPT, RETURNED, CANCELLED;

    private Set<ParcelStatus> next() {
        return switch (this) {
            case REGISTERED -> EnumSet.of(LOADED, CANCELLED);
            case LOADED -> EnumSet.of(OUT_FOR_DELIVERY, REGISTERED, DELIVERED, FAILED_ATTEMPT);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, FAILED_ATTEMPT, LOADED);
            case FAILED_ATTEMPT -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, REGISTERED, RETURNED, LOADED);
            case DELIVERED, RETURNED, CANCELLED -> EnumSet.noneOf(ParcelStatus.class);
        };
    }

    public boolean canTransitionTo(ParcelStatus target) {
        return this == target || next().contains(target);
    }

    public boolean isFinal() {
        return this == DELIVERED || this == RETURNED || this == CANCELLED;
    }
}
