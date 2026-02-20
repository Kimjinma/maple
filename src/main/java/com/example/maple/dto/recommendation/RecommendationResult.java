package com.example.maple.dto.recommendation;

import com.example.maple.domain.UpgradeNode;
import com.example.maple.util.MesoFormatter;
import java.util.List;

public record RecommendationResult(
        String recommendedSetName,
        List<UpgradeNode> requiredItems,
        long totalCost,
        long simulatedScore,
        boolean isTargetReached) {
    public String getFormattedTotalCost() {
        return MesoFormatter.format(totalCost);
    }
}
