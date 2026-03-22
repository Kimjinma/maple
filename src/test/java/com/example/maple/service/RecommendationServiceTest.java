package com.example.maple.service;

import com.example.maple.domain.SetEffectPreset;
import com.example.maple.domain.UpgradeNode;
import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.recommendation.RecommendationResult;
import com.example.maple.dto.stat.DetailStatResponse;
import com.example.maple.repository.SetEffectPresetRepository;
import com.example.maple.repository.UpgradeNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

        @InjectMocks
        private RecommendationService recommendationService;

        @Mock
        private CharacterService characterService;
        @Mock
        private SetEffectPresetRepository setEffectPresetRepository;
        @Mock
        private UpgradeNodeRepository upgradeNodeRepository;
        @Mock
        private EfficiencyCalculatorV3 efficiencyCalculator;

        @Test
        @DisplayName("가성비 테스트: 비싼 칠흑(22성)보다 저렴한 여명(22성)을 우선 추천해야 한다")
        void recommend_ShouldPrioritizeCostEffectiveItems() {
                // Given
                String charName = "TestChar";
                int currentScore = 10000;
                int targetScore = 20000;

                // 1. Mock Basic Inputs
                when(characterService.getBasicByName(charName))
                                .thenReturn(new CharacterBasicResponse("TestChar", "World", "아델", 275));

                when(characterService.getCalcInput(charName)).thenReturn(
                                new CharacterCalcInput(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

                when(characterService.getDetailStatByName(charName))
                                .thenReturn(new DetailStatResponse(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
                when(characterService.getItemOptionSummary(any(), any())).thenReturn(
                                new ItemOptionSummaryResponse(null, null, 0, 0, 0, 0, Collections.emptyMap()));

                // 440억
                UpgradeNode pitch22 = new UpgradeNode();
                pitch22.setId(1L);
                pitch22.setItemName("거대한 공포");
                pitch22.setSlot("반지");
                pitch22.setCostMeso(440_000_000_000L);
                pitch22.setTMainStatFlat(50);

                UpgradeNode dawn22 = new UpgradeNode();
                dawn22.setId(2L);
                dawn22.setItemName("가디언 엔젤 링");
                dawn22.setSlot("반지");
                dawn22.setCostMeso(20_000_000_000L);
                dawn22.setTMainStatFlat(50);

                when(upgradeNodeRepository.findAll()).thenReturn(Arrays.asList(pitch22, dawn22));

                SetEffectPreset mixedPreset = new SetEffectPreset("7칠흑 2여명", 0, 0, 0, 0, 0, 0, 0, 0);
                when(setEffectPresetRepository.findAll()).thenReturn(List.of(mixedPreset));

                lenient().when(efficiencyCalculator.calcActualGainForNode(any(), any(), anyInt(), anyBoolean(),
                                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                                anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                                .thenReturn(20000.0);

                RecommendationResult result = recommendationService.recommend(charName, currentScore, targetScore);

                System.out.println("추천 세트: " + result.recommendedSetName());
                System.out.println("추천 아이템 목록: ");
                result.requiredItems().forEach(item -> System.out.println("- " + item.getItemName()));

                boolean hasDawn = result.requiredItems().stream().anyMatch(i -> i.getItemName().contains("가디언 엔젤 링"));

                assertThat(hasDawn).as("가성비 좋은 여명 아이템(가디언 엔젤 링)이 목록에 보여야 합니다.").isTrue();
        }
}
