package com.example.maple.controller;

import com.example.maple.dto.CharacterBasicResponse;
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
        System.out.println("name = [" + name + "]");
        return characterService.getBasicByName(name);
    }
    @GetMapping("/api/characters/stat")
    public DetailStatResponse stat(@RequestParam String name, @RequestParam(required = false) String date
    ) {
        return characterService.getDetailStatByName(name, date);
    }
}