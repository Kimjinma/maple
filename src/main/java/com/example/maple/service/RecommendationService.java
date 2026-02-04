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
        List<SetEffectPreset> presets = setEffectPresetRepository.findAll();

        RecommendationResult bestArmorResult = null;
        RecommendationResult bestAccessoryResult = null;
        double maxArmorScore = -1;
        long minArmorCost = Long.MAX_VALUE;
        double maxAccessoryScore = -1;
        long minAccessoryCost = Long.MAX_VALUE;

        // 직업별 등
        JobStat jobStat = JobStat.from(basic.characterClass());
        boolean usesMagic = jobStat.usesMagic();
        int baseR = currentScore;

        // 5. 현재 착용 아이템 정보
        ItemOptionSummaryResponse itemSummary = characterService.getItemOptionSummary(characterName,
                basic.characterClass());
        Map<String, ItemOptionSummaryResponse.SlotOption> currentItems = new HashMap<>();
        if (itemSummary != null && itemSummary.slotOptions() != null) {
            itemSummary.slotOptions().forEach((k, v) -> currentItems.put(k.replace(" ", ""), v));
        }

        // 6. 현재 세트 효과 점수 추정 (분리 계산)
        Map<String, Long> currentCounts = new HashMap<>();
        currentItems.values().forEach(opt -> {
            String setName = ItemSetClassifier.classify(opt.itemName());
            if (!setName.equals("NONE")) {
                currentCounts.put(setName, currentCounts.getOrDefault(setName, 0L) + 1);
            }
        });

        double currentArmorScore = getBestArmorScore(currentCounts, presets, basicInput, detail, baseR, usesMagic);
        double currentAccessoryScore = getBestAccessoryScore(currentCounts, presets, basicInput, detail, baseR,
                usesMagic);
        double currentSetScore = currentArmorScore + currentAccessoryScore;

        // 7. 노드 전처리
        List<ProcessedNode> processedNodes = preProcessNodes(allNodes, basicInput, detail, baseR, currentItems,
                usesMagic);

        // 8. 프리셋 별 시뮬레이션
        for (SetEffectPreset preset : presets) {
            boolean isArmor = isArmorPreset(preset.getName());
            boolean isAccessory = isAccessoryPreset(preset.getName());

            if (!isArmor && !isAccessory)
                continue;

            List<String> allowedSets = ItemSetClassifier.getAllowedSets(preset.getName());
            List<ProcessedNode> filteredCandidates = processedNodes;
            if (allowedSets != null) {
                filteredCandidates = processedNodes.stream()
                        .filter(pn -> {
                            String set = ItemSetClassifier.classify(pn.node().getItemName());
                            return allowedSets.contains(set);
                        })
                        .collect(Collectors.toList());
            }

            double potentialTargetSetScore = calculateSetScore(preset, basicInput, detail, baseR, usesMagic);
            double relevantCurrentScore = isArmor ? currentArmorScore : currentAccessoryScore;
            double expectedSetDelta = Math.max(0, potentialTargetSetScore - relevantCurrentScore);
            final double bonusPerItem = expectedSetDelta / 6.0;

            double currentGap = targetScore - currentScore;

            List<ProcessedNode> prioritizedCandidates = new ArrayList<>();
            if (filteredCandidates != null) {
                // Find min cost per item name to identify "Base" items (entry level set items)
                Map<String, Long> minCostMap = filteredCandidates.stream()
                        .collect(Collectors.toMap(
                                pn -> pn.node().getItemName(),
                                pn -> pn.node().getCostMeso(),
                                (c1, c2) -> Math.min(c1, c2)));

                prioritizedCandidates = filteredCandidates.stream()
                        .map(pn -> {
                            String itemName = pn.node().getItemName();
                            String itemSet = ItemSetClassifier.classify(itemName);
                            boolean isKeyItem = false;
                            if (isAccessory) {
                                if (preset.getName().contains("칠흑") && itemSet.equals("PITCH_BOSS"))
                                    isKeyItem = true;
                                if (preset.getName().contains("여명") && itemSet.equals("DAWN_BOSS"))
                                    isKeyItem = true;
                                if (preset.getName().contains("보장") && itemSet.equals("BOSS_ACC"))
                                    isKeyItem = true;
                            }
                            if (isArmor) {
                                if (preset.getName().contains("아케인") && itemSet.equals("ARCANE"))
                                    isKeyItem = true;
                                if (preset.getName().contains("앱솔") && itemSet.equals("ABSO"))
                                    isKeyItem = true;
                                if (preset.getName().contains("에테") && itemSet.equals("ETHERNEL"))
                                    isKeyItem = true;
                                if (preset.getName().contains("루타") && itemSet.equals("ROOT_ABYSS"))
                                    isKeyItem = true;
                            }

                            // Only apply bonus to the lowest cost version (Base Item) to prioritize entry,
                            // but let Starforce upgrades compete on raw efficiency.
                            boolean isBase = pn.node().getCostMeso() == minCostMap.getOrDefault(itemName,
                                    Long.MAX_VALUE);

                            if (isKeyItem && isBase) {
                                return new ProcessedNode(pn.node(), pn.gainScore() + bonusPerItem,
                                        pn.dBoss(), pn.dAtkPct(), pn.dMainPct(), pn.dDmg(), pn.dIed(), pn.dCdmg(),
                                        pn.dCooldown(), pn.dMainFlat(), pn.dAtkOrMag(), pn.appliedSlot());
                            } else {
                                return pn;
                            }
                        })
                        .collect(Collectors.toList());
            }

            long nodeCost = 0;
            List<ProcessedNode> selectedProcessedNodes = new ArrayList<>();

            if (currentGap > 0) {
                List<ProcessedNode> boostedSelection = solveKnapsack(prioritizedCandidates, currentGap, basicInput,
                        detail, baseR, usesMagic);
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

            double nodeGainSum = calculateAggregateNodeGain(selectedNodes, processedNodes, basicInput, detail, baseR,
                    usesMagic);

            Map<String, ItemOptionSummaryResponse.SlotOption> finalItemMap = simulateFinalItems(currentItems,
                    selectedProcessedNodes);
            Map<String, Long> finalCounts = new HashMap<>();
            finalItemMap.values().forEach(opt -> {
                String setName = ItemSetClassifier.classify(opt.itemName());
                if (!setName.equals("NONE")) {
                    finalCounts.put(setName, finalCounts.getOrDefault(setName, 0L) + 1);
                }
            });

            double achievedTargetScore = 0;
            if (isSatisfied(preset.getName(), finalCounts)) {
                achievedTargetScore = calculateSetScore(preset, basicInput, detail, baseR, usesMagic);
            }

            double achievedTotalSetScore;
            if (isArmor) {
                achievedTotalSetScore = achievedTargetScore + currentAccessoryScore;
            } else {
                achievedTotalSetScore = achievedTargetScore + currentArmorScore;
            }

            double realSetEffectDelta = achievedTotalSetScore - currentSetScore;
            double finalSimulatedScore = currentScore + realSetEffectDelta + nodeGainSum;

            RecommendationResult result = new RecommendationResult(preset.getName(), selectedNodes, nodeCost,
                    (long) finalSimulatedScore, finalSimulatedScore >= targetScore);

            if (isArmor) {
                if (finalSimulatedScore > maxArmorScore) {
                    maxArmorScore = finalSimulatedScore;
                    minArmorCost = nodeCost;
                    bestArmorResult = result;
                } else if (finalSimulatedScore == maxArmorScore && nodeCost < minArmorCost) {
                    maxArmorScore = finalSimulatedScore;
                    minArmorCost = nodeCost;
                    bestArmorResult = result;
                } else if (bestArmorResult == null) {
                    bestArmorResult = result;
                }
            } else if (isAccessory) {
                if (finalSimulatedScore > maxAccessoryScore) {
                    maxAccessoryScore = finalSimulatedScore;
                    minAccessoryCost = nodeCost;
                    bestAccessoryResult = result;
                } else if (finalSimulatedScore == maxAccessoryScore && nodeCost < minAccessoryCost) {
                    maxAccessoryScore = finalSimulatedScore;
                    minAccessoryCost = nodeCost;
                    bestAccessoryResult = result;
                } else if (bestAccessoryResult == null) {
                    bestAccessoryResult = result;
                }
            }
        }

        // Combine Best Results
        List<UpgradeNode> combinedNodes = new ArrayList<>();
        long combinedCost = 0;
        String combinedName = "";

        if (bestArmorResult != null) {
            combinedNodes.addAll(bestArmorResult.requiredItems());
            combinedCost += bestArmorResult.totalCost();
            combinedName += bestArmorResult.recommendedSetName();
        }
        if (bestAccessoryResult != null) {
            if (bestArmorResult != null) {
                // Check if Accessory overrides Armor slots (should not, but safety)
                // Or if combined cost is too high? No constraint given.
            }
            combinedNodes.addAll(bestAccessoryResult.requiredItems());
            combinedCost += bestAccessoryResult.totalCost();
            if (!combinedName.isEmpty())
                combinedName += " + ";
            combinedName += bestAccessoryResult.recommendedSetName();
        }

        if (combinedNodes.isEmpty()) {
            return new RecommendationResult("현재 장비 유지", Collections.emptyList(), 0, (long) currentScore, true);
        }

        // Final Score Recalculation (Robust)
        // Since we merged best armor and best accessory plans, and they operate on
        // disjoint sets,
        // the total score gain is roughly sum of gains.
        // But to be precise, we re-simulated. But re-simulation logic needs
        // ProcessedNodes.
        // We can approximate safely:
        double finalScore = currentScore;
        if (bestArmorResult != null)
            finalScore += (bestArmorResult.simulatedScore() - currentScore);
        if (bestAccessoryResult != null)
            finalScore += (bestAccessoryResult.simulatedScore() - currentScore);

        // Note: The above approximation assumes the baseline (currentScore) is removed
        // from both, so we add delta.
        // BestResult.Final = Base + Delta. => Delta = Final - Base.
        // Total = Base + DeltaArmor + DeltaAcc.
        // Correct.

        return new RecommendationResult(combinedName.isEmpty() ? "유지" : combinedName, combinedNodes, combinedCost,
                (long) finalScore, finalScore >= targetScore);
    }

    // --- Helper Logic ---

    private double getBestArmorScore(Map<String, Long> counts, List<SetEffectPreset> presets,
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {

        double dbScore = presets.stream()
                .filter(p -> isArmorPreset(p.getName()))
                .filter(p -> isSatisfied(p.getName(), counts))
                .mapToDouble(p -> calculateSetScore(p, input, detail, baseR, usesMagic))
                .max().orElse(0.0);

        // Manual Calculation for mixed sets fallback
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

        // Manual Calculation for disjoint sets (Pitch/Dawn/Boss)
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
            CharacterCalcInput input, DetailStatResponse detail, int baseR, boolean usesMagic) {

        Map<String, List<ProcessedNode>> alternativesBySlot = nodes.stream()
                .collect(Collectors.groupingBy(pn -> pn.appliedSlot));

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

            if (itemGain >= targetGap)
                break;

            String bestSlot = null;
            double bestEff = -1;

            for (String slot : alternativesBySlot.keySet()) {
                List<ProcessedNode> candidates = alternativesBySlot.get(slot);
                int currentIndex = currentChoice.get(slot);

                if (currentIndex + 1 < candidates.size()) {
                    ProcessedNode next = candidates.get(currentIndex + 1);
                    ProcessedNode current = (currentIndex == -1) ? null : candidates.get(currentIndex);

                    double gainDiff = next.gainScore - (current == null ? 0 : current.gainScore);
                    long costDiff = next.node.getCostMeso() - (current == null ? 0 : current.node.getCostMeso());

                    if (costDiff <= 0)
                        costDiff = 1;

                    double eff = gainDiff / costDiff;
                    if (eff > bestEff && gainDiff > 0) {
                        bestEff = eff;
                        bestSlot = slot;
                    }
                }
            }

            if (bestSlot == null)
                break;
            currentChoice.put(bestSlot, currentChoice.get(bestSlot) + 1);
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
            return gainScore / node.getCostMeso();
        }
    }

    private List<ProcessedNode> preProcessNodes(List<UpgradeNode> nodes, CharacterCalcInput input,
            DetailStatResponse detail, int baseR, Map<String, ItemOptionSummaryResponse.SlotOption> currentItems,
            boolean usesMagic) {

        List<ProcessedNode> result = new ArrayList<>();

        for (UpgradeNode node : nodes) {
            List<String> targetSlots = new ArrayList<>();
            String rawSlot = node.getSlot().replace(" ", "");

            if (rawSlot.equals("반지") || rawSlot.equals("링")) {
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

                if (currentItems != null) {
                    var cur = currentItems.get(ts);
                    if (cur != null) {
                        cBoss = cur.bossDamage();
                        cAtkPct = usesMagic ? cur.magicPct() : cur.attackPct();
                        cMainPct = cur.mainStatPct();
                        cDmg = cur.damage();
                        cIed = cur.ignoreDefense();
                        cFlatM = cur.mainStat();
                        cFlatA = usesMagic ? cur.magicPower() : cur.attackPower();
                    }
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
                .filter(pn -> pn.gainScore() > 0)
                .sorted(Comparator.comparingDouble(ProcessedNode::efficiency).reversed())
                .collect(Collectors.toList());
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
}
