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
import com.example.maple.dto.recommendation.ProcessedNode;
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
    private final SetEffectEvaluator setEffectEvaluator;
    private final UpgradeNodePreprocessor nodePreprocessor;
    private final OptimizationOrchestrator optimizationOrchestrator;

    public RecommendationResult recommend(String characterName, int currentScore, int targetScore) {
        var basic = characterService.getBasicByName(characterName);
        CharacterCalcInput basicInput = characterService.getCalcInput(characterName);
        DetailStatResponse detail = characterService.getDetailStatByName(characterName);

        List<UpgradeNode> allNodes = upgradeNodeRepository.findAll();
        Map<String, String> itemToSetGroup = allNodes.stream()
                .filter(n -> n.getSetGroup() != null)
                .collect(Collectors.toMap(UpgradeNode::getItemName, UpgradeNode::getSetGroup, (a, b) -> a));

        List<SetEffectPreset> allPresets = setEffectPresetRepository.findAll();

        JobStat jobStat = JobStat.from(basic.characterClass());
        boolean usesMagic = jobStat.usesMagic();
        int baseR = currentScore;

        ItemOptionSummaryResponse itemSummary = characterService.getItemOptionSummary(characterName, basic.characterClass());
        Map<String, ItemOptionSummaryResponse.SlotOption> currentItems = new HashMap<>();
        if (itemSummary != null && itemSummary.slotOptions() != null) {
            itemSummary.slotOptions().forEach((k, v) -> currentItems.put(k.replace(" ", ""), v));
        }

        Map<String, Long> currentCounts = new HashMap<>();
        currentItems.values().forEach(opt -> {
            String setName = itemToSetGroup.get(opt.itemName());
            if (setName != null) currentCounts.merge(setName, 1L, Long::sum);
        });

        double currentTotalSetScore = setEffectEvaluator.calculateTotalSetScore(
                currentCounts, allPresets, basicInput, detail, baseR, usesMagic);

        List<ProcessedNode> processedNodes = nodePreprocessor.preProcessNodes(
                allNodes, basicInput, detail, baseR, currentItems, usesMagic);

        return optimizationOrchestrator.findBestCombination(targetScore, currentScore,
                currentTotalSetScore, allPresets, processedNodes, currentItems, itemToSetGroup,
                basicInput, detail, baseR, usesMagic);
    }
}
