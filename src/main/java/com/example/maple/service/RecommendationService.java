package com.example.maple.service;

import com.example.maple.domain.JobStat;
import com.example.maple.domain.SetEffectPreset;
import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.recommendation.RecommendationResult;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.repository.SetEffectPresetRepository;
import com.example.maple.repository.UpgradeNodeRepository;
import com.example.maple.util.ItemSetClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final CharacterService characterService;
    private final SetEffectPresetRepository setEffectPresetRepository;
    private final UpgradeNodeRepository upgradeNodeRepository;
    private final EfficiencyCalculatorV3 efficiencyCalculator;

    public RecommendationResult recommend(String characterName, int currentScore, int targetScore) {
        // 1. 캐릭터 기본 정보 조회
        var basic = characterService.getBasicByName(characterName);
        CharacterCalcInput basicInput = characterService.getCalcInput(characterName);

        // 2. 상세 스탯 조회
        DetailStatResponse detail = characterService.getDetailStatByName(characterName);

        // 3. 업그레이드 노드 전체 로드
        List<UpgradeNode> allNodes = upgradeNodeRepository.findAll();

        // 4. 세트 효과 프리셋 로드
        List<SetEffectPreset> allPresets = setEffectPresetRepository.findAll();
        List<SetEffectPreset> armorPresets = allPresets.stream().filter(p -> isArmorPreset(p.getName()))
                .collect(Collectors.toList());

        boolean hasEthernal = armorPresets.stream().anyMatch(p -> p.getName().contains("에테"));
        if (!hasEthernal) {
            armorPresets.add(new SetEffectPreset("3에테 5아케인 여명 보장", 0, 0, 0, 0, 0, 0, 0, 0));
        }

        List<SetEffectPreset> accessoryPresets = allPresets.stream().filter(p -> isAccessoryPreset(p.getName()))
                .collect(Collectors.toList());

        JobStat jobStat = JobStat.from(basic.characterClass());
        boolean usesMagic = jobStat.usesMagic();
        int baseR = currentScore;

        ItemOptionSummaryResponse itemSummary = characterService.getItemOptionSummary(characterName,
                basic.characterClass());
        Map<String, ItemOptionSummaryResponse.SlotOption> currentItems = new HashMap<>();
        if (itemSummary != null && itemSummary.slotOptions() != null) {
            itemSummary.slotOptions().forEach((k, v) -> currentItems.put(k.replace(" ", ""), v));
        }

        Map<String, Long> currentCounts = new HashMap<>();
        currentItems.values().forEach(opt -> {
            String setName = ItemSetClassifier.classify(opt.itemName());
            if (!setName.equals("NONE")) {
                currentCounts.put(setName, currentCounts.getOrDefault(setName, 0L) + 1);
            }
        });

        int currentArmorTier = 0;
        if (currentCounts.getOrDefault("ETHERNEL", 0L) >= 1)
            currentArmorTier = 4;
        else if (currentCounts.getOrDefault("ARCANE", 0L) >= 1)
            currentArmorTier = 3;
        else if (currentCounts.getOrDefault("ABSO", 0L) >= 1)
            currentArmorTier = 2;
        else if (currentCounts.getOrDefault("ROOT_ABYSS", 0L) >= 1)
            currentArmorTier = 1;

        double currentArmorScore = getBestArmorScore(currentCounts, allPresets, basicInput, detail, baseR, usesMagic);
        double currentAccessoryScore = getBestAccessoryScore(currentCounts, allPresets, basicInput, detail, baseR,
                usesMagic);
        double currentTotalSetScore = currentArmorScore + currentAccessoryScore;

        List<ProcessedNode> processedNodes = preProcessNodes(allNodes, basicInput, detail, baseR, currentItems,
                usesMagic);

        RecommendationResult bestResult = null;
        double maxSimulatedScore = -1;
        long minCost = Long.MAX_VALUE;

        for (SetEffectPreset armorPreset : armorPresets) {

            int presetTier = 0;
            if (armorPreset.getName().contains("에테"))
                presetTier = 4;
            else if (armorPreset.getName().contains("아케인"))
                presetTier = 3;
            else if (armorPreset.getName().contains("앱솔"))
                presetTier = 2;
            else if (armorPreset.getName().contains("루타") || armorPreset.getName().contains("카루타"))
                presetTier = 1;

            if (presetTier < currentArmorTier) {
                continue;
            }

            for (SetEffectPreset accPreset : accessoryPresets) {

                List<String> allowedArmorSets = ItemSetClassifier.getAllowedSets(armorPreset.getName());
                List<String> allowedAccSets = ItemSetClassifier.getAllowedSets(accPreset.getName());

                Set<String> combinedAllowedSets = new HashSet<>();
                combinedAllowedSets.addAll(allowedArmorSets);
                combinedAllowedSets.addAll(allowedAccSets);

                List<ProcessedNode> filteredCandidates = processedNodes.stream()
                        .filter(pn -> {
                            String set = ItemSetClassifier.classify(pn.node().getItemName());

                            boolean isMajorSet = set.equals("ROOT_ABYSS") || set.equals("ABSO") || set.equals("ARCANE")
                                    || set.equals("ETHERNEL");

                            if (isMajorSet) {

                                return combinedAllowedSets.contains(set);
                            }

                            if (!combinedAllowedSets.isEmpty()) {
                                return combinedAllowedSets.contains(set);
                            }

                            return true;
                        })
                        .collect(Collectors.toList());

                double targetArmorSetScore = calculateSetScore(armorPreset, basicInput, detail, baseR, usesMagic);
                double targetAccSetScore = calculateSetScore(accPreset, basicInput, detail, baseR, usesMagic);

                double expectedSetDelta = Math.max(0, (targetArmorSetScore + targetAccSetScore) - currentTotalSetScore);

                final double bonusPerItem = expectedSetDelta / 10.0; // roughly 10 items involved

                double currentGap = targetScore - currentScore;

                List<ProcessedNode> prioritizedCandidates = new ArrayList<>();
                if (!filteredCandidates.isEmpty()) {

                    prioritizedCandidates = filteredCandidates.stream()
                            .map(pn -> {
                                String itemName = pn.node().getItemName();
                                String itemSet = ItemSetClassifier.classify(itemName);

                                boolean isTargetSet = false;
                                if (isArmorPreset(armorPreset.getName())) {
                                    if (armorPreset.getName().contains("아케인") && itemSet.equals("ARCANE"))
                                        isTargetSet = true;
                                    else if (armorPreset.getName().contains("앱솔") && itemSet.equals("ABSO"))
                                        isTargetSet = true;
                                    else if (armorPreset.getName().contains("에테") && itemSet.equals("ETHERNEL"))
                                        isTargetSet = true;
                                    else if (armorPreset.getName().contains("루타") && itemSet.equals("ROOT_ABYSS"))
                                        isTargetSet = true;
                                }

                                if (isAccessoryPreset(accPreset.getName())) {
                                    if (accPreset.getName().contains("칠흑") && itemSet.equals("PITCH_BOSS"))
                                        isTargetSet = true;
                                    else if (accPreset.getName().contains("여명") && itemSet.equals("DAWN_BOSS"))
                                        isTargetSet = true;
                                    else if (accPreset.getName().contains("보장") && itemSet.equals("BOSS_ACC"))
                                        isTargetSet = true;
                                    else if (accPreset.getName().contains("마이") && itemSet.equals("MYSTERIOUS"))
                                        isTargetSet = true;
                                }

                                if (isTargetSet) {
                                    return new ProcessedNode(pn.node(), pn.gainScore() + bonusPerItem,
                                            pn.dBoss(), pn.dAtkPct(), pn.dMainPct(), pn.dDmg(), pn.dIed(), pn.dCdmg(),
                                            pn.dCooldown(), pn.dMainFlat(), pn.dAtkOrMag(), pn.appliedSlot());
                                }
                                return pn;
                            })
                            .collect(Collectors.toList());
                }

                long nodeCost = 0;
                List<ProcessedNode> selectedProcessedNodes = new ArrayList<>();

                if (currentGap > 0) {
                    Function<List<ProcessedNode>, Double> setGainCalculator = (selectedNodes) -> {
                        Map<String, ItemOptionSummaryResponse.SlotOption> finalItemMap = simulateFinalItems(
                                currentItems, selectedNodes);
                        Map<String, Long> finalCounts = new HashMap<>();
                        finalItemMap.values().forEach(opt -> {
                            String setName = ItemSetClassifier.classify(opt.itemName());
                            if (!setName.equals("NONE")) {
                                finalCounts.put(setName, finalCounts.getOrDefault(setName, 0L) + 1);
                            }
                        });

                        ItemOptionSummaryResponse.SlotOption weapon = finalItemMap.get("무기");
                        if (weapon == null && finalItemMap.containsKey("Weapon"))
                            weapon = finalItemMap.get("Weapon");

                        if (weapon != null && weapon.itemName().contains("제네시스")) {
                            if (finalCounts.getOrDefault("ROOT_ABYSS", 0L) >= 3)
                                finalCounts.put("ROOT_ABYSS", finalCounts.get("ROOT_ABYSS") + 1);

                            if (finalCounts.getOrDefault("ETHERNEL", 0L) >= 3)
                                finalCounts.put("ETHERNEL", finalCounts.get("ETHERNEL") + 1);

                            if (finalCounts.getOrDefault("ARCANE", 0L) >= 3)
                                finalCounts.put("ARCANE", finalCounts.get("ARCANE") + 1);

                            if (finalCounts.getOrDefault("ABSO", 0L) >= 3)
                                finalCounts.put("ABSO", finalCounts.get("ABSO") + 1);
                        }

                        boolean armorSatisfied = isSatisfied(armorPreset.getName(), finalCounts);
                        boolean accSatisfied = isSatisfied(accPreset.getName(), finalCounts);

                        double gain = 0;
                        if (armorSatisfied)
                            gain += Math.max(0, targetArmorSetScore - currentArmorScore);
                        if (accSatisfied)
                            gain += Math.max(0, targetAccSetScore - currentAccessoryScore);

                        return gain;
                    };

                    List<ProcessedNode> boostedSelection = solveKnapsack(prioritizedCandidates, currentGap, basicInput,
                            detail, baseR, usesMagic, setGainCalculator);

                    for (ProcessedNode boosted : boostedSelection) {
                        ProcessedNode original = processedNodes.stream()
                                .filter(p -> p.node().getId().equals(boosted.node().getId())
                                        && p.appliedSlot().equals(boosted.appliedSlot()))
                                .findFirst().orElse(null);
                        if (original != null)
                            selectedProcessedNodes.add(original);
                    }
                    nodeCost = selectedProcessedNodes.stream().mapToLong(pn -> pn.node.getCostMeso()).sum();
                }

                List<UpgradeNode> selectedNodes = selectedProcessedNodes.stream().map(ProcessedNode::node)
                        .collect(Collectors.toList());

                double nodeGainSum = calculateAggregateNodeGain(selectedNodes, processedNodes, basicInput, detail,
                        baseR, usesMagic);
                Map<String, ItemOptionSummaryResponse.SlotOption> finalItemMap = simulateFinalItems(currentItems,
                        selectedProcessedNodes);
                Map<String, Long> finalCounts = new HashMap<>();
                finalItemMap.values().forEach(opt -> {
                    String setName = ItemSetClassifier.classify(opt.itemName());
                    if (!setName.equals("NONE"))
                        finalCounts.put(setName, finalCounts.getOrDefault(setName, 0L) + 1);
                });

                double achievedSetScore = 0;
                if (isSatisfied(armorPreset.getName(), finalCounts))
                    achievedSetScore += calculateSetScore(armorPreset, basicInput, detail, baseR, usesMagic);
                else
                    achievedSetScore += currentArmorScore;

                if (isSatisfied(accPreset.getName(), finalCounts))
                    achievedSetScore += calculateSetScore(accPreset, basicInput, detail, baseR, usesMagic);
                else
                    achievedSetScore += currentAccessoryScore;

                double realSetEffectDelta = achievedSetScore - currentTotalSetScore;
                double finalSimulatedScore = currentScore + realSetEffectDelta + nodeGainSum;

                RecommendationResult result = new RecommendationResult(
                        armorPreset.getName() + " + " + accPreset.getName(),
                        selectedNodes, nodeCost, (long) finalSimulatedScore, finalSimulatedScore >= targetScore);

                boolean replace = false;
                if (bestResult == null) {
                    replace = true;
                } else {
                    boolean newSatisfies = finalSimulatedScore >= targetScore;
                    boolean oldSatisfies = bestResult.simulatedScore() >= targetScore;

                    if (newSatisfies && oldSatisfies) {
                        if (nodeCost < minCost)
                            replace = true;
                        else if (nodeCost == minCost && finalSimulatedScore > maxSimulatedScore)
                            replace = true;
                    } else if (newSatisfies && !oldSatisfies) {
                        replace = true;
                    } else if (!newSatisfies && oldSatisfies) {
                        replace = false;
                    } else {
                        if (finalSimulatedScore > maxSimulatedScore)
                            replace = true;
                    }
                }

                if (replace) {
                    maxSimulatedScore = finalSimulatedScore;
                    minCost = nodeCost;
                    bestResult = result;
                }
            }
        }

        if (bestResult == null) {
            return new RecommendationResult("유지", Collections.emptyList(), 0, (long) currentScore, true);
        }

        return bestResult;
    }

    private double getBestArmorScore(Map<String, Long> counts, List<SetEffectPreset> presets,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {

        double dbScore = presets.stream()
                .filter(p -> isArmorPreset(p.getName()))
                .filter(p -> isSatisfied(p.getName(), counts))
                .mapToDouble(p -> calculateSetScore(p, input, detail, baseR, usesMagic))
                .max().orElse(0.0);

        double manualScore = 0;
        if (counts.getOrDefault("ROOT_ABYSS", 0L) >= 3) {
            manualScore += efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50);
        }

        double highTier = 0;
        if (counts.getOrDefault("ETHERNEL", 0L) >= 5) {
            highTier = Math.max(highTier, efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic,
                    30, 0, 0, 0, 0, 20, 0, 0, 0, 50, 100));
        } else if (counts.getOrDefault("ARCANE", 0L) >= 5) {
            highTier = Math.max(highTier, efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic,
                    30, 0, 0, 0, 0, 10, 0, 0, 0, 50, 135));
        } else if (counts.getOrDefault("ABSO", 0L) >= 5) {
            highTier = Math.max(highTier, efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic,
                    30, 0, 0, 0, 0, 10, 0, 0, 0, 30, 95));
        }
        manualScore += highTier;

        return Math.max(dbScore, manualScore);
    }

    private double getBestAccessoryScore(Map<String, Long> counts, List<SetEffectPreset> presets,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {

        double dbScore = presets.stream()
                .filter(p -> isAccessoryPreset(p.getName()))
                .filter(p -> isSatisfied(p.getName(), counts))
                .mapToDouble(p -> calculateSetScore(p, input, detail, baseR, usesMagic))
                .max().orElse(0.0);

        double pitchScore = 0;
        long pitchCount = counts.getOrDefault("PITCH_BOSS", 0L);
        if (pitchCount >= 8)
            pitchScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 15, 0, 0, 15, 0,
                    15, 15, 0, 0, 50, 60);
        else if (pitchCount >= 4)
            pitchScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 0, 0, 0, 15, 0, 15,
                    15, 0, 0, 0, 0);
        else if (pitchCount >= 2)
            pitchScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 0, 0, 0, 0, 0, 10,
                    0, 0, 0, 10, 10);

        double dawnScore = 0;
        long dawnCount = counts.getOrDefault("DAWN_BOSS", 0L);
        if (dawnCount >= 4)
            dawnScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 10, 0, 0, 0, 0, 10,
                    0, 0, 0, 0, 20);
        else if (dawnCount >= 2)
            dawnScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 10, 0, 0, 0, 0, 0,
                    0, 0, 0, 10, 10);

        double bossScore = 0;
        long bossCount = counts.getOrDefault("BOSS_ACC", 0L);
        if (bossCount >= 9)
            bossScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 10, 0, 0, 0, 0, 10,
                    5, 0, 0, 0, 20);
        else if (bossCount >= 7)
            bossScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 10, 0, 0, 0, 0, 10,
                    0, 0, 0, 0, 20);
        else if (bossCount >= 3)
            bossScore = efficiencyCalculator.calcActualGainForNode(input, detail, baseR, usesMagic, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 10, 10);

        double sumManual = pitchScore + dawnScore + bossScore;

        return Math.max(dbScore, sumManual);
    }

    private boolean isArmorPreset(String name) {
        return name.contains("루타") || name.contains("앱솔") || name.contains("아케인") || name.contains("에테");
    }

    private boolean isAccessoryPreset(String name) {
        return name.contains("보장") || name.contains("여명") || name.contains("칠흑");
    }

    private List<ProcessedNode> solveKnapsack(List<ProcessedNode> nodes, double targetGap,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic,
            Function<List<ProcessedNode>, Double> setGainCalculator) {

        Map<String, List<ProcessedNode>> alternativesBySlot = nodes.stream()
                .collect(Collectors.groupingBy(pn -> pn.appliedSlot));

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
                    String candidateName = candidate.node.getItemName();

                    boolean isDuplicate = false;
                    for (Map.Entry<String, Integer> entry : currentChoice.entrySet()) {
                        String otherSlot = entry.getKey();
                        int otherIdx = entry.getValue();

                        if (otherSlot.equals(slot))
                            continue;

                        if (otherIdx != -1) {
                            ProcessedNode otherSelected = alternativesBySlot.get(otherSlot).get(otherIdx);
                            if (otherSelected.node.getItemName().replace(" ", "")
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

                    double gainDiff = next.gainScore - (current == null ? 0 : current.gainScore);
                    long costDiff = next.node.getCostMeso() - (current == null ? 0 : current.node.getCostMeso());

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
                // [FIX] 효율(Efficiency) 기반으로 더 이상 고를 게 없지만, 목표 점수엔 도달 못했을 경우
                // "가성비는 나쁘지만 점수는 확실히 오르는" 아이템(고점 갱신)을 찾아서 강제로 뚫어줍니다.
                if (itemGain + setGain < targetGap) {
                    for (String slot : alternativesBySlot.keySet()) {
                        List<ProcessedNode> candidates = alternativesBySlot.get(slot);
                        int currentIndex = currentChoice.get(slot);

                        // 현재 선택된 것보다 '점수(Gain)'가 더 높은 게 있는지 확인 (가성비 무시)
                        for (int i = candidates.size() - 1; i > currentIndex; i--) {
                            ProcessedNode candidate = candidates.get(i);
                            ProcessedNode current = (currentIndex == -1) ? null : candidates.get(currentIndex);

                            // 중복 체크
                            String candidateName = candidate.node.getItemName();
                            boolean isDuplicate = false;
                            for (Map.Entry<String, Integer> entry : currentChoice.entrySet()) {
                                if (entry.getKey().equals(slot))
                                    continue;
                                if (entry.getValue() != -1) {
                                    ProcessedNode other = alternativesBySlot.get(entry.getKey()).get(entry.getValue());
                                    if (other.node.getItemName().replace(" ", "")
                                            .equals(candidateName.replace(" ", ""))) {
                                        isDuplicate = true;
                                        break;
                                    }
                                }
                            }
                            if (isDuplicate)
                                continue;

                            // 단순히 점수가 오르면 바로 선택 (Best Gain)
                            double gainDiff = candidate.gainScore - (current == null ? 0 : current.gainScore);
                            if (gainDiff > 0) {
                                bestSlot = slot;
                                bestNextIndex = i;
                                // 제일 쎈 놈 하나 찾았으면 바로 탈출 (가장 큰 상승폭 하나만 적용)
                                break;
                            }
                        }
                        if (bestSlot != null)
                            break;
                    }
                }

                // 그래도 없으면 진짜 끝
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

    private Map<String, ItemOptionSummaryResponse.SlotOption> simulateFinalItems(
            Map<String, ItemOptionSummaryResponse.SlotOption> currentItems, List<ProcessedNode> selectedNodes) {

        Map<String, ItemOptionSummaryResponse.SlotOption> result = new HashMap<>(currentItems);

        for (ProcessedNode pn : selectedNodes) {
            String slot = pn.appliedSlot;
            ItemOptionSummaryResponse.SlotOption dummy = new ItemOptionSummaryResponse.SlotOption(
                    pn.node.getItemName(), // itemName
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            result.put(slot, dummy);
        }
        return result;
    }

    private double calculateAggregateNodeGain(List<UpgradeNode> selected, List<ProcessedNode> allProcessed,
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
            ProcessedNode p = allProcessed.stream().filter(pn -> pn.node.equals(u)).findFirst().orElse(null);
            if (p != null) {
                sMainPct += p.dMainPct;
                sAtkPct += p.dAtkPct;
                sBoss += p.dBoss;
                sDmg += p.dDmg;
                sIed += p.dIed;
                sCdmg += p.dCdmg;
                sCooldown += p.dCooldown;
                sMainFlat += p.dMainFlat;
                sAtkFlat += p.dAtkOrMag;
            }
        }

        return efficiencyCalculator.calcActualGainForNode(
                input, detail, baseR, usesMagic,
                sBoss, sAtkPct, sMainPct, sDmg, 0, sIed, sCdmg, 0, sCooldown, sMainFlat, sAtkFlat);
    }

    record ProcessedNode(UpgradeNode node, double gainScore,
            double dBoss, double dAtkPct, double dMainPct, double dDmg, double dIed, double dCdmg,
            int dCooldown, int dMainFlat, int dAtkOrMag, String appliedSlot) {
        public double efficiency() {
            if (node.getCostMeso() == 0)
                return Double.MAX_VALUE;

            double eff = gainScore / node.getCostMeso();

            return eff;
        }
    }

    private List<ProcessedNode> preProcessNodes(List<UpgradeNode> nodes, CharacterCalcInput input,
            DetailStatResponse detail, int baseR, Map<String, ItemOptionSummaryResponse.SlotOption> currentItems,
            boolean usesMagic) {

        List<ProcessedNode> result = new ArrayList<>();

        for (UpgradeNode node : nodes) {
            List<String> targetSlots = new ArrayList<>();
            String rawSlot = node.getSlot().replace(" ", "");

            if (rawSlot.equals("반지")) {
                targetSlots.add("반지1");
                targetSlots.add("반지2");
                targetSlots.add("반지3");
                targetSlots.add("반지4");
            } else if (rawSlot.equals("펜던트")) {
                targetSlots.add("펜던트1");
                targetSlots.add("펜던트2");
            } else {
                targetSlots.add(rawSlot);
            }

            double nBoss = node.getTBossPct();
            double nAtkPct = node.getTAtkPct() + (usesMagic ? node.getTMagicPct() : 0);
            double nMainPct = node.getTMainStatPct();
            double nDmg = node.getTDmgPct();
            double nIed = node.getTIedPct();
            double nCdmg = node.getTCdmgPct();
            double nAll = node.getTAllStatPct();
            int nCool = node.getTCooldownSec();
            int nFlatM = node.getTMainStatFlat();
            int nFlatA = usesMagic ? node.getTMagicFlat() : node.getTAtkFlat();

            for (String ts : targetSlots) {
                double cBoss = 0, cAtkPct = 0, cMainPct = 0, cDmg = 0, cIed = 0, cCdmg = 0, cAll = 0;
                int cCool = 0, cFlatM = 0, cFlatA = 0;

                ItemOptionSummaryResponse.SlotOption cur = null;
                if (currentItems != null) {
                    cur = findCurrentItem(currentItems, ts);
                }

                if (cur != null) {
                    cBoss = cur.bossDamage();
                    cAtkPct = usesMagic ? cur.magicPct() : cur.attackPct();
                    cMainPct = cur.mainStatPct();
                    cDmg = cur.damage();
                    cIed = cur.ignoreDefense();
                    cFlatM = cur.mainStat();
                    cFlatA = usesMagic ? cur.magicPower() : cur.attackPower();
                }

                double totalNodeMainPct = nMainPct + nAll;
                double dMain = totalNodeMainPct - cMainPct;

                double dBoss = nBoss - cBoss;
                double dAtkPct = nAtkPct - cAtkPct;
                double dDmg = nDmg - cDmg;
                double dIed = nIed - cIed;
                double dCdmg = nCdmg - cCdmg;
                double dCool = nCool - cCool;

                double dFlatM = nFlatM - cFlatM;
                double dFlatA = nFlatA - cFlatA;

                double score = efficiencyCalculator.calcActualGainForNode(
                        input, detail, baseR, usesMagic,
                        dBoss, dAtkPct, dMain, dDmg, 0, dIed, dCdmg, 0, (int) dCool, dFlatM, dFlatA);

                result.add(new ProcessedNode(node, score, dBoss, dAtkPct, dMain, dDmg, dIed, dCdmg, (int) dCool,
                        (int) dFlatM, (int) dFlatA, ts));
            }
        }

        return result.stream()
                // .filter(pn -> pn.gainScore() > 0) // [FIX] 마이너스 이득(스탯 하락)이라도 세트 효과 때문에 필요할 수
                // 있음
                .sorted(Comparator.comparingDouble(ProcessedNode::efficiency).reversed()).collect(Collectors.toList());

    }

    private double calculateSetScore(SetEffectPreset preset, CharacterCalcInput input, DetailStatResponse detail,
            int baseR, boolean usesMagic) {
        int dMainFlat = preset.getMainStat() + preset.getAllStat();
        return efficiencyCalculator.calcActualGainForNode(
                input, detail, baseR, usesMagic,
                preset.getBossDamage(), 0, 0, preset.getDamage(), 0, preset.getIgnoreDefense(),
                preset.getCriticalDamage(), 0, 0, dMainFlat, preset.getAttackPower());
    }

    private boolean isSatisfied(String presetName, Map<String, Long> counts) {
        int pitch = extractCount(presetName, "칠흑");
        if (pitch > 0 && counts.getOrDefault("PITCH_BOSS", 0L) < pitch)
            return false;

        int dawn = extractCount(presetName, "여명");
        if (dawn > 0 && counts.getOrDefault("DAWN_BOSS", 0L) < dawn)
            return false;

        int root = extractCount(presetName, "카루타");
        if (root == 0)
            root = extractCount(presetName, "루타");
        if (root == 0 && presetName.contains("3카"))
            root = 3;
        if (root == 0 && presetName.contains("4카"))
            root = 4;
        if (root > 0 && counts.getOrDefault("ROOT_ABYSS", 0L) < root)
            return false;

        int abso = extractCount(presetName, "앱솔");
        if (abso > 0 && counts.getOrDefault("ABSO", 0L) < abso)
            return false;

        int arcane = extractCount(presetName, "아케인");
        if (arcane == 0 && presetName.contains("5아"))
            arcane = 5;
        if (arcane > 0 && counts.getOrDefault("ARCANE", 0L) < arcane)
            return false;

        int ether = extractCount(presetName, "에테");
        if (ether > 0 && counts.getOrDefault("ETHERNEL", 0L) < ether)
            return false;

        int boss = extractCount(presetName, "보장");
        if (boss == 0)
            boss = extractCount(presetName, "보스장신구");
        if (boss > 0 && counts.getOrDefault("BOSS_ACC", 0L) < boss)
            return false;

        return true;
    }

    private int extractCount(String text, String key) {
        try {
            int idx = text.indexOf(key);
            if (idx > 0 && Character.isDigit(text.charAt(idx - 1))) {
                return Character.getNumericValue(text.charAt(idx - 1));
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private ItemOptionSummaryResponse.SlotOption findCurrentItem(
            Map<String, ItemOptionSummaryResponse.SlotOption> currentItems, String slotKey) {
        if (currentItems.containsKey(slotKey))
            return currentItems.get(slotKey);

        String normalized = slotKey.replace(" ", "").trim();
        if (currentItems.containsKey(normalized))
            return currentItems.get(normalized);

        List<String> aliases = getSlotAliases(normalized);
        for (String alias : aliases) {
            String nAlias = alias.replace(" ", "");
            if (currentItems.containsKey(nAlias))
                return currentItems.get(nAlias);
        }
        return null;
    }

    private List<String> getSlotAliases(String slot) {
        if (slot.equals("반지"))
            return List.of("반지1", "반지2", "반지3", "반지4");
        if (slot.equals("펜던트"))
            return List.of("펜던트", "펜던트2");

        // Armor Variants (Korean Only)
        if (slot.equals("상의"))
            return List.of("상의", "옷(상의)");
        if (slot.equals("하의"))
            return List.of("하의", "옷(하의)", "바지");
        if (slot.equals("모자"))
            return List.of("모자");
        if (slot.equals("얼굴장식"))
            return List.of("얼굴장식");
        if (slot.equals("눈장식"))
            return List.of("눈장식");
        if (slot.equals("귀고리"))
            return List.of("귀고리");
        if (slot.equals("벨트"))
            return List.of("벨트");
        if (slot.equals("신발"))
            return List.of("신발");
        if (slot.equals("장갑"))
            return List.of("장갑");
        if (slot.equals("어깨장식") || slot.equals("견장"))
            return List.of("어깨장식", "견장");
        if (slot.equals("망토"))
            return List.of("망토");

        return List.of();
    }
}
