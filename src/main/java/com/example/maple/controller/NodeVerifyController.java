package com.example.maple.controller;

import com.example.maple.domain.JobStat;
import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.eff.EfficiencyResponse;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.repository.UpgradeNodeRepository;
import com.example.maple.service.CharacterService;
import com.example.maple.service.EfficiencyCalculator;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class NodeVerifyController {

    private final CharacterService characterService;
    private final UpgradeNodeRepository nodeRepo;

    public NodeVerifyController(CharacterService characterService,
                                UpgradeNodeRepository nodeRepo) {
        this.characterService = characterService;
        this.nodeRepo = nodeRepo;
    }

    @GetMapping("/node-verify")
    public Map<String, Object> verify(
            @RequestParam String name,
            @RequestParam long nodeId,
            @RequestParam int r,
            @RequestParam(defaultValue = "380") int bossDef
    ) {
        UpgradeNode n = nodeRepo.findById(nodeId).orElseThrow();

        // 1) 캐릭터 입력 데이터(서비스에 이미 있음)
        CharacterCalcInput calc = characterService.getCalcInput(name);
        DetailStatResponse detail = characterService.getDetailStatByName(name);

        boolean isMage = (JobStat.from(calc.characterClass()) == JobStat.MAGE);

        // 2) 효율표(민감도)
        EfficiencyResponse eff = characterService.getEfficiency(name, r, bossDef);

        // 3) 선형 예측 (Σ Δ × eff) + breakdown
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("boss",     eff.boss1() * n.getDeltaBossPct());
        breakdown.put("atkPct",   eff.atk1()  * n.getDeltaAtkPct());
        breakdown.put("statPct",  eff.stat1() * n.getDeltaStatPct());
        breakdown.put("finalDmg", eff.fd1()   * n.getDeltaFinalDmgPct());
        breakdown.put("ied",      eff.ied1()  * n.getDeltaIedPct());
        breakdown.put("cdmg",     eff.cdmg1() * n.getDeltaCdmgPct());
        breakdown.put("allstat",  eff.all1()  * n.getDeltaAllstatPct());
        breakdown.put("cooldown", eff.cooldown1() * n.getDeltaCooldownSec());

        double linearGain = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();

        // 4) 실제 재계산 (power 기반)
        EfficiencyCalculator calculator = new EfficiencyCalculator(bossDef);
        double actualGain = calculator.calcActualGainForNode(
                calc, detail, r, isMage,
                n.getDeltaBossPct(),
                n.getDeltaAtkPct(),
                n.getDeltaStatPct(),
                n.getDeltaFinalDmgPct(),
                n.getDeltaIedPct(),
                n.getDeltaCdmgPct(),
                n.getDeltaAllstatPct(),
                n.getDeltaCooldownSec()
        );

        double diffPct = (actualGain == 0.0) ? 0.0 : ((linearGain - actualGain) / actualGain) * 100.0;

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("character", name);
        res.put("class", calc.characterClass());
        res.put("baseR", r);
        res.put("bossDef", bossDef);
        res.put("nodeId", n.getId());
        res.put("nodeName", n.getName());
        res.put("eff", eff);
        res.put("breakdown", breakdown);
        res.put("linearGain", linearGain);
        res.put("actualGain", actualGain);
        res.put("diffPct", diffPct);
        return res;

    }
}
