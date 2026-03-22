package com.example.maple.service;

import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.recommendation.ProcessedNode;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.util.SlotAliasUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UpgradeNodePreprocessor {

    private final EfficiencyCalculatorV3 efficiencyCalculator;

    public List<ProcessedNode> preProcessNodes(List<UpgradeNode> nodes, CharacterCalcInput input,
            DetailStatResponse detail, int baseR, Map<String, ItemOptionSummaryResponse.SlotOption> currentItems,
            boolean usesMagic) {

        List<ProcessedNode> result = new ArrayList<>();

        for (UpgradeNode node : nodes) {
            List<String> targetSlots = resolveSlots(node.getSlot().replace(" ", ""));

            double nAtkPct = node.getTAtkPct() + (usesMagic ? node.getTMagicPct() : 0);
            double nMainPct = node.getTMainStatPct() + node.getTAllStatPct();
            double nCdmg = node.getTCdmgPct();
            int nFlatM = node.getTMainStatFlat();
            int nFlatA = usesMagic ? node.getTMagicFlat() : node.getTAtkFlat();

            for (String ts : targetSlots) {
                ItemOptionSummaryResponse.SlotOption cur = currentItems != null
                        ? SlotAliasUtil.findCurrentItem(currentItems, ts) : null;

                double cBoss = 0, cAtkPct = 0, cMainPct = 0, cDmg = 0, cIed = 0;
                int cFlatM = 0, cFlatA = 0;

                if (cur != null) {
                    cBoss = cur.bossDamage();
                    cAtkPct = usesMagic ? cur.magicPct() : cur.attackPct();
                    cMainPct = cur.mainStatPct();
                    cDmg = cur.damage();
                    cIed = cur.ignoreDefense();
                    cFlatM = cur.mainStat();
                    cFlatA = usesMagic ? cur.magicPower() : cur.attackPower();
                }

                double dBoss = -cBoss;
                double dAtkPct = nAtkPct - cAtkPct;
                double dMainPct = nMainPct - cMainPct;
                double dDmg = -cDmg;
                double dIed = -cIed;
                double dCdmg = nCdmg;
                double dFlatM = nFlatM - cFlatM;
                double dFlatA = nFlatA - cFlatA;

                if (cur != null && cur.isSeedRing()) {
                    dFlatM = -9999999;
                }

                double score = efficiencyCalculator.calcActualGainForNode(
                        input, detail, baseR, usesMagic,
                        dBoss, dAtkPct, dMainPct, dDmg, 0, dIed, dCdmg, 0, 0, dFlatM, dFlatA);

                result.add(new ProcessedNode(node, score, dBoss, dAtkPct, dMainPct, dDmg, dIed, dCdmg, 0,
                        (int) dFlatM, (int) dFlatA, ts));
            }
        }

        return result.stream()
                .sorted(Comparator.comparingDouble(ProcessedNode::efficiency).reversed())
                .collect(Collectors.toList());
    }

    private List<String> resolveSlots(String rawSlot) {
        if (rawSlot.equals("반지")) return List.of("반지1", "반지2", "반지3", "반지4");
        if (rawSlot.equals("펜던트")) return List.of("펜던트1", "펜던트2");
        return List.of(rawSlot);
    }
}
