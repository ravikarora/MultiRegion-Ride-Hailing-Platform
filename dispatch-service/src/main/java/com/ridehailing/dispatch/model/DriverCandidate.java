package com.ridehailing.dispatch.model;

import lombok.Builder;
import lombok.Data;

/**
 * Candidate driver evaluated during dispatch matching.
 */
@Data
@Builder
public class DriverCandidate {

    private String driverId;
    private double distanceKm;
    private double rating;
    private double declineRate;
    private String tier;
    private double score;

    /**
     * Composite score: closer + higher rating + lower decline rate = better.
     * α=0.5, β=0.3, γ=0.2 are tunable weights.
     */
    public static double computeScore(double distanceKm, double rating, double declineRate) {
        double alpha = 0.5;
        double beta  = 0.3;
        double gamma = 0.2;
        double safeDistance = Math.max(distanceKm, 0.01);
        double safeDecline  = Math.max(declineRate, 0.01);
        return alpha * (1.0 / safeDistance) + beta * rating + gamma * (1.0 / safeDecline);
    }
}
