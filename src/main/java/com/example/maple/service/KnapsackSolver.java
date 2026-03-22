package com.example.maple.service;

import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.recommendation.ProcessedNode;
import com.example.maple.dto.stat.DetailStatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KnapsackSolver {

    private final EfficiencyCalculatorV3 efficiencyCalculator;

    public List<ProcessedNode> solveKnapsack(List<ProcessedNode> nodes, double targetGap,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic,
            Function<List<ProcessedNode>, Double> setGainCalculator) {

        Map<String, List<ProcessedNode>> alternativesBySlot = nodes.stream()
                .collect(Collectors.groupingBy(ProcessedNode::appliedSlot));

        alternativesBySlot.forEach((slot, list) -> {
            list.sort(Comparator.comparingDouble(ProcessedNode::gainScore));
        });

        Map<String, Integer> currentChoice = new HashMap<>();
        alternativesBySlot.keySet().forEach(k -> currentChoice.put(k, -1));

        while (true) {
            List<ProcessedNode> currentSelected = currentChoice.entrySet().stream()
                    .filter(e -> e.getValue() != -1)
                    .map(e -> alternativesBySlot.get(e.getKey()).get(e.getValue()))
                    .collect(Collectors.toList());

            List<UpgradeNode> currentNodes = currentSelected.stream().map(ProcessedNode::node)
                    .collect(Collectors.toList());

            double itemGain = calculateAggregateNodeGain(currentNodes, nodes, input, detail, baseR, usesMagic);
            double setGain = setGainCalculator.apply(currentSelected);

            if (itemGain + setGain >= targetGap)
                break;

            String bestSlot = null;
            double bestEff = -1;
            int bestNextIndex = -1;

            for (String slot : alternativesBySlot.keySet()) {
                List<ProcessedNode> candidates = alternativesBySlot.get(slot);
                int currentIndex = currentChoice.get(slot);

                int nextIndex = -1;
                for (int i = currentIndex + 1; i < candidates.size(); i++) {
                    ProcessedNode candidate = candidates.get(i);
                    String candidateName = candidate.node().getItemName();

                    boolean isDuplicate = false;
                    for (Map.Entry<String, Integer> entry : currentChoice.entrySet()) {
                        String otherSlot = entry.getKey();
                        int otherIdx = entry.getValue();

                        if (otherSlot.equals(slot))
                            continue;

                        if (otherIdx != -1) {
                            ProcessedNode otherSelected = alternativesBySlot.get(otherSlot).get(otherIdx);
                            if (otherSelected.node().getItemName().replace(" ", "")
                                    .equals(candidateName.replace(" ", ""))) {
                                isDuplicate = true;
                                break;
                            }
                        }
                    }

                    if (!isDuplicate) {
                        nextIndex = i;
                        break;
                    }
                }

                if (nextIndex != -1) {
                    ProcessedNode next = candidates.get(nextIndex);
                    ProcessedNode current = (currentIndex == -1) ? null : candidates.get(currentIndex);

                    double gainDiff = next.gainScore() - (current == null ? 0 : current.gainScore());
                    long costDiff = next.node().getCostMeso() - (current == null ? 0 : current.node().getCostMeso());

                    if (costDiff <= 0)
                        costDiff = 1;

                    double eff = gainDiff / costDiff;
                    if (eff > bestEff && gainDiff > 0) {
                        bestEff = eff;
                        bestSlot = slot;
                        bestNextIndex = nextIndex;
                    }
                }
            }

            if (bestSlot == null) {
                if (itemGain + setGain < targetGap) {
                    for (String slot : alternativesBySlot.keySet()) {
                        List<ProcessedNode> candidates = alternativesBySlot.get(slot);
                        int currentIndex = currentChoice.get(slot);

                        for (int i = candidates.size() - 1; i > currentIndex; i--) {
                            ProcessedNode candidate = candidates.get(i);
                            ProcessedNode current = (currentIndex == -1) ? null : candidates.get(currentIndex);

                            String candidateName = candidate.node().getItemName();
                            boolean isDuplicate = false;
                            for (Map.Entry<String, Integer> entry : currentChoice.entrySet()) {
                                if (entry.getKey().equals(slot))
                                    continue;
                                if (entry.getValue() != -1) {
                                    ProcessedNode other = alternativesBySlot.get(entry.getKey()).get(entry.getValue());
                                    if (other.node().getItemName().replace(" ", "")
                                            .equals(candidateName.replace(" ", ""))) {
                                        isDuplicate = true;
                                        break;
                                    }
                                }
                            }
                            if (isDuplicate)
                                continue;

                            double gainDiff = candidate.gainScore() - (current == null ? 0 : current.gainScore());
                            if (gainDiff > 0) {
                                bestSlot = slot;
                                bestNextIndex = i;
                                break;
                            }
                        }
                        if (bestSlot != null)
                            break;
                    }
                }

                if (bestSlot == null)
                    break;
            }
            currentChoice.put(bestSlot, bestNextIndex);
        }

        return currentChoice.entrySet().stream()
                .filter(e -> e.getValue() != -1)
                .map(e -> alternativesBySlot.get(e.getKey()).get(e.getValue()))
                .collect(Collectors.toList());
    }

    public double calculateAggregateNodeGain(List<UpgradeNode> selected, List<ProcessedNode> allProcessed,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {
        if (selected.isEmpty())
            return 0;

        double sMainPct = 0;
        double sAtkPct = 0;
        double sBoss = 0;
        double sDmg = 0;
        double sIed = 0;
        double sCdmg = 0;
        int sCooldown = 0;
        int sMainFlat = 0;
        int sAtkFlat = 0;

        for (UpgradeNode u : selected) {
            ProcessedNode p = allProcessed.stream().filter(pn -> pn.node().equals(u)).findFirst().orElse(null);
            if (p != null) {
                sMainPct += p.dMainPct();
                sAtkPct += p.dAtkPct();
                sBoss += p.dBoss();
                sDmg += p.dDmg();
                sIed += p.dIed();
                sCdmg += p.dCdmg();
                sCooldown += p.dCooldown();
                sMainFlat += p.dMainFlat();
                sAtkFlat += p.dAtkOrMag();
            }
        }

        return efficiencyCalculator.calcActualGainForNode(
                input, detail, baseR, usesMagic,
                sBoss, sAtkPct, sMainPct, sDmg, 0, sIed, sCdmg, 0, sCooldown, sMainFlat, sAtkFlat);
    }
}
