package com.ridehailing.shared.util;

import com.uber.h3core.H3Core;

import java.io.IOException;
import java.util.List;

/**
 * H3 hexagonal geo-cell utilities.
 * Resolution 8 ≈ 0.74 km² — fine-grained surge pricing cells.
 * Resolution 9 ≈ 0.10 km² — dispatch geo-fencing.
 */
public final class H3Util {

    public static final int SURGE_RESOLUTION = 8;
    public static final int DISPATCH_RESOLUTION = 9; // ~0.1 km² for fine-grained matching

    private static final H3Core h3;

    static {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialise H3Core", e);
        }
    }

    private H3Util() {}

    public static String latLngToCell(double lat, double lng, int resolution) {
        return h3.latLngToCellAddress(lat, lng, resolution);
    }

    public static String surgeCell(double lat, double lng) {
        return latLngToCell(lat, lng, SURGE_RESOLUTION);
    }

    public static String dispatchCell(double lat, double lng) {
        return latLngToCell(lat, lng, DISPATCH_RESOLUTION);
    }

    public static List<String> kRingCells(String cellId, int ringSize) {
        return h3.gridDisk(cellId, ringSize);
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
