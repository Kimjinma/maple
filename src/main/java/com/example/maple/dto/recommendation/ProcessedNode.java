package com.example.maple.dto.recommendation;

import com.example.maple.domain.UpgradeNode;

public record ProcessedNode(UpgradeNode node, double gainScore,
        double dBoss, double dAtkPct, double dMainPct, double dDmg, double dIed, double dCdmg,
        int dCooldown, int dMainFlat, int dAtkOrMag, String appliedSlot) {
    public double efficiency() {
        if (node.getCostMeso() == 0)
            return Double.MAX_VALUE;

        return gainScore / node.getCostMeso();
    }
}
