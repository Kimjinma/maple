package com.example.maple.controller;

import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.eff.EfficiencyResponse;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.item.ItemStat;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.service.CharacterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping("/api/characters/basic")
    public CharacterBasicResponse basic(@RequestParam String name) {
        return characterService.getBasicByName(name); // ✅ wrapper로 다시 제공
    }

    @GetMapping("/api/characters/stat")
    public DetailStatResponse stat(
            @RequestParam String name,
            @RequestParam(required = false) String date
    ) {
        return characterService.getDetailStatByName(name);
    }

    @GetMapping("/api/characters/item-equipment")
    public ItemStat itemEquipment(@RequestParam String name) {
        return characterService.getItemStat(name); // ✅ wrapper로 다시 제공
    }

    @GetMapping("/api/characters/item-option-summary")
    public ItemOptionSummaryResponse itemOptionSummary(@RequestParam String name) {

        // 1) 직업 포함된 stat을 한 번만 조회
        DetailStatResponse stat = characterService.getDetailStatByName(name);

        // 2) 직업을 함께 넘겨서 item 요약 계산
        return characterService.getItemOptionSummary(name, stat.characterClass());
    }

    @GetMapping("/api/characters/calc-input")
    public CharacterCalcInput calcInput(@RequestParam String name) {
        return characterService.getCalcInput(name);
    }

    @GetMapping("/api/characters/efficiency")
    public EfficiencyResponse efficiency(
            @RequestParam String name,
            @RequestParam int r,
            @RequestParam(defaultValue = "380") int bossDef
    ) {
        return characterService.getEfficiency(name, r, bossDef);
    }
}
