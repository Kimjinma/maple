package com.example.maple.controller;

import com.example.maple.domain.JobStat;
import com.example.maple.dto.recommendation.RecommendationResult;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.service.CharacterService;
import com.example.maple.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CalculatorController {

    private final CharacterService characterService;
    private final RecommendationService recommendationService;

    // 1. 입력 폼 페이지
    @GetMapping("/calculator")
    public String showForm() {
        return "calculator-form";
    }


    @PostMapping("/calculator/result")
    public String showResult(
            @RequestParam String name,
            @RequestParam String currentScore, // String으로 받아서 직접 파싱
            @RequestParam String targetScore, // String으로 받아서 직접 파싱
            Model model) {

        int current, target;
        try {
            // 숫자 형식 검증
            current = Integer.parseInt(currentScore);
            target = Integer.parseInt(targetScore);
        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "환산 점수는 숫자만 입력해주세요.");
            return "calculator-form";
        }

        // 1. 환산 점수 범위 검증 (1만 ~ 10만)
        if (current < 10000 || current > 100000 || target < 10000 || target > 100000) {
            model.addAttribute("errorMessage", "환산 점수는 1만 ~ 10만 사이로 입력해주세요.");
            return "calculator-form";
        }

        RecommendationResult result;
        try {
            result = recommendationService.recommend(name, current, target);
        } catch (Exception e) {
            // 2. 닉네임 조회 실패 및 API 오류 예외 처리
            // Nexon API에서 400 Bad Request가 발생하면 여기서 잡힙니다.
            String msg = e.getMessage();
            if (msg != null && msg.contains("400")) {
                msg = "존재하지 않는 캐릭터 닉네임입니다.";
            } else {
                msg = "정보를 불러올 수 없습니다. (" + msg + ")";
            }

            model.addAttribute("errorMessage", msg);
            return "calculator-form";
        }

        // 직업에 따른 라벨 동적 처리
        DetailStatResponse detail = characterService.getDetailStatByName(name);

        JobStat jobStat = JobStat.from(detail.characterClass());
        String mainStatName = jobStat.getMainStat();
        String atkOrMagName = jobStat.usesMagic() ? "마력" : "공격력";

        model.addAttribute("result", result);
        model.addAttribute("jobStat", jobStat);
        model.addAttribute("mainStatName", mainStatName);
        model.addAttribute("atkOrMagName", atkOrMagName);

        return "calculator-result";
    }
}




