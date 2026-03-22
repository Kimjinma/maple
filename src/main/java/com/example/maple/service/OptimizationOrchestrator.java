package com.example.maple.service;

import com.example.maple.domain.SetEffectPreset;
import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.recommendation.RecommendationResult;
import com.example.maple.dto.recommendation.ProcessedNode;
import com.example.maple.dto.stat.DetailStatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OptimizationOrchestrator {

    private final SetEffectEvaluator setEffectEvaluator;
    private final KnapsackSolver knapsackSolver;

    public RecommendationResult findBestCombination(int targetScore, int currentScore,
            double currentTotalSetScore,
            List<SetEffectPreset> allPresets, List<ProcessedNode> processedNodes,
            Map<String, ItemOptionSummaryResponse.SlotOption> currentItems,
            Map<String, String> itemToSetGroup, CharacterCalcInput basicInput, DetailStatResponse detail,
            int baseR, boolean usesMagic) {

        // ===== DB에서 슬롯 그룹 자동 추출 =====
        // 1. 각 슬롯에 어떤 세트들이 경쟁하는지 파악
        Map<String, Set<String>> slotToSets = new HashMap<>();
        for (ProcessedNode pn : processedNodes) {
            String set = pn.node().getSetGroup();
            if (set != null)
                slotToSets.computeIfAbsent(pn.appliedSlot(), k -> new HashSet<>()).add(set);
        }

        // 2. 같은 경쟁세트를 가진 슬롯끼리 그룹화 (2개 이상 세트가 경쟁하는 그룹만)
        Map<Set<String>, Set<String>> competitorToSlots = new HashMap<>();
        slotToSets.forEach((slot, sets) -> {
            if (sets.size() > 1)
                competitorToSlots.computeIfAbsent(sets, k -> new HashSet<>()).add(slot);
        });

        // 3. 슬롯 그룹 목록 (예: [{모자,상의,하의}, {장갑,신발,견장,망토}])
        List<Set<String>> slotGroups = new ArrayList<>(competitorToSlots.values());
        List<Set<String>> setsPerGroup = new ArrayList<>(competitorToSlots.keySet());

        // 4. 조합 자동 생성 (각 그룹에서 세트 하나씩 선택 → 카르테시안 곱)
        List<String[]> combos = generateCombos(setsPerGroup, 0, new String[setsPerGroup.size()]);

        // 5. 각 그룹별 현재 유저 세트 감지
        String[] currentSets = new String[slotGroups.size()];
        for (int i = 0; i < slotGroups.size(); i++)
            currentSets[i] = detectGroupSet(currentItems, itemToSetGroup, slotGroups.get(i), setsPerGroup.get(i));

        RecommendationResult bestResult = null;
        double maxScore = -1;
        long minCost = Long.MAX_VALUE;

        for (String[] combo : combos) {
            // 필터링: 각 그룹 슬롯에 해당 세트만 허용
            List<ProcessedNode> filtered = processedNodes.stream()
                    .filter(pn -> isAllowed(pn, combo, slotGroups, setsPerGroup))
                    .collect(Collectors.toList());

            // ALL-OR-NOTHING 세트 전환
            List<ProcessedNode> mandatory = new ArrayList<>();
            boolean skip = false;
            for (int i = 0; i < slotGroups.size() && !skip; i++) {
                List<ProcessedNode> m = collectMandatory(currentSets[i], combo[i], slotGroups.get(i),
                        currentItems, itemToSetGroup, filtered);
                if (m == null) { skip = true; } else { mandatory.addAll(m); }
            }
            if (skip) continue;

            Set<String> mSlots = mandatory.stream().map(ProcessedNode::appliedSlot).collect(Collectors.toSet());
            List<ProcessedNode> remaining = filtered.stream()
                    .filter(pn -> !mSlots.contains(pn.appliedSlot())).collect(Collectors.toList());

            final List<ProcessedNode> fm = mandatory;
            List<ProcessedNode> selected = new ArrayList<>(fm);
            double gap = targetScore - currentScore;

            if (gap > 0) {
                Function<List<ProcessedNode>, Double> setCalc = sel -> {
                    List<ProcessedNode> all = new ArrayList<>(fm);
                    all.addAll(sel);
                    Map<String, Long> counts = countSets(simulateFinal(currentItems, all), itemToSetGroup);
                    return setEffectEvaluator.calculateTotalSetScore(counts, allPresets, basicInput, detail, baseR, usesMagic)
                            - currentTotalSetScore;
                };
                double mGain = fm.stream().mapToDouble(ProcessedNode::gainScore).sum();
                if (gap - mGain > 0 && !remaining.isEmpty()) {
                    for (ProcessedNode b : knapsackSolver.solveKnapsack(remaining, gap - mGain, basicInput, detail, baseR, usesMagic, setCalc))
                        processedNodes.stream()
                                .filter(p -> p.node().getId().equals(b.node().getId()) && p.appliedSlot().equals(b.appliedSlot()))
                                .findFirst().ifPresent(selected::add);
                }
            }

            List<UpgradeNode> nodes = selected.stream().map(ProcessedNode::node).collect(Collectors.toList());
            double nodeGain = knapsackSolver.calculateAggregateNodeGain(nodes, processedNodes, basicInput, detail, baseR, usesMagic);
            long cost = selected.stream().mapToLong(pn -> pn.node().getCostMeso()).sum();
            double setScore = setEffectEvaluator.calculateTotalSetScore(
                    countSets(simulateFinal(currentItems, selected), itemToSetGroup),
                    allPresets, basicInput, detail, baseR, usesMagic);
            double sim = currentScore + (setScore - currentTotalSetScore) + nodeGain;

            if (sim <= currentScore) continue;

            String name = String.join("+", combo);
            RecommendationResult result = new RecommendationResult(name, nodes, cost, (long) sim, sim >= targetScore);
            if (bestResult == null || isBetter(sim, maxScore, cost, minCost, targetScore)) {
                maxScore = sim;
                minCost = cost;
                bestResult = result;
            }
        }
        return bestResult != null ? bestResult
                : new RecommendationResult("유지", Collections.emptyList(), 0, (long) currentScore, true);
    }

    /** 카르테시안 곱으로 세트 조합 생성 */
    private List<String[]> generateCombos(List<Set<String>> setsPerGroup, int depth, String[] current) {
        List<String[]> result = new ArrayList<>();
        if (depth == setsPerGroup.size()) {
            result.add(current.clone());
            return result;
        }
        for (String set : setsPerGroup.get(depth)) {
            current[depth] = set;
            result.addAll(generateCombos(setsPerGroup, depth + 1, current));
        }
        return result;
    }

    private boolean isAllowed(ProcessedNode pn, String[] combo, List<Set<String>> slotGroups, List<Set<String>> setsPerGroup) {
        String set = pn.node().getSetGroup();
        if (set == null) return true;
        String slot = pn.appliedSlot();
        for (int i = 0; i < slotGroups.size(); i++)
            if (slotGroups.get(i).contains(slot) && setsPerGroup.get(i).contains(set))
                return set.equals(combo[i]);
        return true;
    }

    private List<ProcessedNode> collectMandatory(String curSet, String targetSet, Set<String> slotGroup,
            Map<String, ItemOptionSummaryResponse.SlotOption> currentItems,
            Map<String, String> itemToSetGroup, List<ProcessedNode> candidates) {
        if (curSet == null || curSet.equals(targetSet)) return new ArrayList<>();
        List<ProcessedNode> result = new ArrayList<>();
        currentItems.forEach((slot, opt) -> {
            if (slotGroup.contains(slot) && curSet.equals(itemToSetGroup.get(opt.itemName())))
                candidates.stream()
                        .filter(pn -> pn.appliedSlot().equals(slot) && targetSet.equals(pn.node().getSetGroup()))
                        .max(Comparator.comparingDouble(ProcessedNode::gainScore))
                        .ifPresent(result::add);
        });
        long needed = currentItems.entrySet().stream()
                .filter(e -> slotGroup.contains(e.getKey()) && curSet.equals(itemToSetGroup.get(e.getValue().itemName())))
                .count();
        return result.size() >= needed ? result : null;
    }

    private String detectGroupSet(Map<String, ItemOptionSummaryResponse.SlotOption> items,
            Map<String, String> itemToSetGroup, Set<String> slotGroup, Set<String> validSets) {
        Map<String, Long> counts = new HashMap<>();
        items.forEach((slot, opt) -> {
            if (slotGroup.contains(slot)) {
                String s = itemToSetGroup.get(opt.itemName());
                if (s != null && validSets.contains(s)) counts.merge(s, 1L, Long::sum);
            }
        });
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private Map<String, Long> countSets(Map<String, ItemOptionSummaryResponse.SlotOption> items, Map<String, String> map) {
        Map<String, Long> c = new HashMap<>();
        items.values().forEach(o -> { String s = map.get(o.itemName()); if (s != null) c.merge(s, 1L, Long::sum); });
        return c;
    }

    private boolean isBetter(double nS, double oS, long nC, long oC, int t) {
        boolean nOk = nS >= t, oOk = oS >= t;
        if (nOk && oOk) return nC < oC || (nC == oC && nS > oS);
        return nOk || (!oOk && nS > oS);
    }

    private Map<String, ItemOptionSummaryResponse.SlotOption> simulateFinal(
            Map<String, ItemOptionSummaryResponse.SlotOption> cur, List<ProcessedNode> sel) {
        Map<String, ItemOptionSummaryResponse.SlotOption> r = new HashMap<>(cur);
        for (ProcessedNode pn : sel)
            r.put(pn.appliedSlot(), new ItemOptionSummaryResponse.SlotOption(
                    pn.node().getItemName(), 0.0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, false));
        return r;
    }
}
