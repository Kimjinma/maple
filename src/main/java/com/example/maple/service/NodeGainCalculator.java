package com.example.maple.service;

import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.eff.EfficiencyResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NodeGainCalculator {

    public Map<String, Double> breakdown(UpgradeNode n, EfficiencyResponse e) {
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("boss", e.boss1() * n.getDeltaBossPct());
        b.put("atkPct", e.atk1() * n.getDeltaAtkPct());
        b.put("statPct", e.stat1() * n.getDeltaStatPct());
        b.put("finalDmg", e.fd1() * n.getDeltaFinalDmgPct());
        b.put("ied", e.ied1() * n.getDeltaIedPct());
        b.put("cdmg", e.cdmg1() * n.getDeltaCdmgPct());
        b.put("allstat", e.all1() * n.getDeltaAllstatPct());
        b.put("cooldown", e.cooldown1() * n.getDeltaCooldownSec());
        return b;
    }

    public double total(Map<String, Double> breakdown) {
        return breakdown.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
