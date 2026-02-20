package com.example.maple.util;

import java.util.ArrayList;
import java.util.List;

public class ItemSetClassifier {

    public static String classify(String itemName) {
        if (itemName == null)
            return "NONE";
        String name = itemName.replace(" ", ""); // 공백 제거 후 비교

        // 1. 칠흑의 보스 세트
        if (containsAny(name, "창세의뱃지", "창세의", "마도서", "거대한공포", "고통의근원", "몽환의벨트", "루즈컨트롤", "커맨더포스",
                "마력이깃든안대", "블랙하트", "미트라의분노")) {
            return "PITCH_BOSS"; // 칠흑
        }

        // 2. 여명의 보스 세트
        if (containsAny(name, "트와일라이트", "에스텔라", "데이브레이크", "가디언엔젤")) {
            return "DAWN_BOSS"; // 여명
        }

        // 3. 보스 장신구 세트 (주요한 것만)
        if (containsAny(name, "응축된힘의", "아쿠아틱", "블랙빈마크", "파풀라투스", "지옥의불꽃", "데아시두스", "골든클로버",
                "혼테일", "매커네이터", "도미네이터", "카혼목", "실버블라썸", "고귀한이피아", "분노한자쿰", "크리스탈벤투스")) {
            return "BOSS_ACC";
        }

        // 4. 방어구/무기 세트
        if (name.contains("아케인셰이드"))
            return "ARCANE";
        if (name.contains("앱솔랩스"))
            return "ABSO";
        if (name.contains("에테르넬"))
            return "ETHERNEL";
        if (name.contains("하이네스") || name.contains("이글아이") || name.contains("트릭스터") || name.contains("파프니르")) {
            return "ROOT_ABYSS"; // 카루타
        }

        if (name.contains("마이스터"))
            return "MEISTER";

        return "NONE"; // 세트 없음 or 기타
    }

    private static boolean containsAny(String target, String... keywords) {
        for (String k : keywords) {
            if (target.contains(k))
                return true;
        }
        return false;
    }

    // 프리셋 이름에서 허용할 세트 목록 추출
    public static List<String> getAllowedSets(String presetName) {
        boolean mentionsPitch = presetName.contains("칠흑");
        boolean mentionsDawn = presetName.contains("여명");
        boolean mentionsBoss = presetName.contains("보장") || presetName.contains("보스장신구");

        boolean mentionsArcane = presetName.contains("아케인");
        boolean mentionsAbso = presetName.contains("앱솔");
        boolean mentionsEther = presetName.contains("에테");
        boolean mentionsRoot = presetName.contains("루타");

        List<String> allowed = new ArrayList<>();
        allowed.add("NONE"); // 세트 없는 템은 항상 허용

        // 장신구 계열 제약
        if (mentionsPitch) {
            allowed.add("PITCH_BOSS");
        }
        if (mentionsDawn) {
            allowed.add("DAWN_BOSS");
        }
        if (mentionsBoss) {
            allowed.add("BOSS_ACC");
        }

        // 방어구 계열 제약
        if (mentionsArcane) {
            allowed.add("ARCANE");
        }
        if (mentionsAbso) {
            allowed.add("ABSO");
        }
        if (mentionsEther) {
            allowed.add("ETHERNEL");
        }
        if (mentionsRoot) {
            allowed.add("ROOT_ABYSS");
        }

        return allowed;
    }
}
