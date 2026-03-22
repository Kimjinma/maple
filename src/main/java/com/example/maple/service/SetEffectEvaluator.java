package com.example.maple.service;

import com.example.maple.domain.SetEffectPreset;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.stat.DetailStatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SetEffectEvaluator {

    private final EfficiencyCalculatorV3 efficiencyCalculator;

    public double calculateSetScore(SetEffectPreset preset, CharacterCalcInput input, DetailStatResponse detail,
            int baseR, boolean usesMagic) {
        int dMainFlat = preset.getMainStat() + preset.getAllStat();
        return efficiencyCalculator.calcActualGainForNode(
                input, detail, baseR, usesMagic,
                preset.getBossDamage(), 0, 0, preset.getDamage(), 0, preset.getIgnoreDefense(),
                preset.getCriticalDamage(), 0, 0, dMainFlat, preset.getAttackPower());
    }

    public boolean isSatisfied(SetEffectPreset preset, Map<String, Long> counts) {
        return counts.getOrDefault(preset.getSetGroup(), 0L) >= preset.getReqCount();
    }

    public double calculateTotalSetScore(Map<String, Long> counts, List<SetEffectPreset> allPresets,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {

        Map<String, List<SetEffectPreset>> byGroup = allPresets.stream()
                .collect(Collectors.groupingBy(SetEffectPreset::getSetGroup));

        double total = 0;
        for (var entry : byGroup.entrySet()) {
            double best = entry.getValue().stream()
                    .filter(p -> isSatisfied(p, counts))
                    .mapToDouble(p -> calculateSetScore(p, input, detail, baseR, usesMagic))
                    .max().orElse(0.0);
            total += best;
        }
        return total;
    }
}
