package com.example.maple.service;

import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.eff.EfficiencyResponse;
import com.example.maple.dto.stat.DetailStatResponse;

/**
 * 환산 증가량 계산기
 *
 * - 넥슨 API 값에는 도핑이 없으므로 (운영 기준) 도핑을 기본 적용한다.
 * - 올스탯%는 주스탯%로 취급한다.
 * - 쿨감 1초 = 주스탯 12%로 치환한다.
 * - 과증폭 방지: ln(P1/P0) 대신 (P1-P0)/P0 사용
 * - 공+10 / 주스탯+10은 %효율 기반 변환
 */
public class EfficiencyCalculator {

    private final int bossDef;

    // 옵션별 완화 가중치(초기값)
    private static final double W_BOSS = 0.55;
    private static final double W_ATK_PCT = 0.95;
    private static final double W_STAT_PCT = 0.65;
    private static final double W_FD = 1.15;
    private static final double W_CDMG = 1.05;
    private static final double W_IED = 0.08;

    // 플랫 변환 계수
    private static final double K_ATK10_FROM_ATK1 = 0.70;
    private static final double K_STAT10_FROM_STAT1 = 0.85;

    // 규칙
    private static final int COOLDOWN_TO_STAT_PCT = 6;

    public EfficiencyCalculator(int bossDef) {
        this.bossDef = bossDef;
    }

    /** ✅ 운영용: 도핑 무조건 적용 */
    public EfficiencyResponse calc(CharacterCalcInput c, DetailStatResponse d, int baseR, boolean isMage) {
        return calcInternal(c, d, baseR, isMage, true);
    }

    /** ✅ 검증용: 스샷처럼 이미 도핑 포함이면 applyDoping=false로 호출 */
    public EfficiencyResponse calcInternal(
            CharacterCalcInput c,
            DetailStatResponse d,
            int baseR,
            boolean isMage,
            boolean applyDoping
    ) {
        Snapshot base = Snapshot.from(c, d, isMage, applyDoping);

        double p0 = power(base);

        // 상대 증가율: (P1-P0)/P0
        java.util.function.Function<Snapshot, Double> relGrowth =
                s1 -> (power(s1) - p0) / p0;

        double boss1 = baseR * relGrowth.apply(base.withBossPct(base.bossPct + 1)) * W_BOSS;
        double atk1  = baseR * relGrowth.apply(base.withAtkPct(base.atkPct + 1)) * W_ATK_PCT;
        double stat1 = baseR * relGrowth.apply(base.withStatPct(base.statPct + 1)) * W_STAT_PCT;
        double fd1   = baseR * relGrowth.apply(base.withFinalDmgPct(base.finalDmgPct + 1)) * W_FD;
        double cdmg1 = baseR * relGrowth.apply(base.withCritDmgPct(base.critDmgPct + 1)) * W_CDMG;
        double ied1  = baseR * relGrowth.apply(base.withIedPct(base.iedPct + 1)) * W_IED;

        // 올스탯 1% = 주스탯 1%
        double all1 = stat1;

        // 쿨감 -1초 = 주스탯 6%
        double cooldown1 = stat1 * COOLDOWN_TO_STAT_PCT;

        // 플랫(+10)은 %효율 기반 변환
        double atk10  = atk1 * K_ATK10_FROM_ATK1;
        double stat10 = stat1 * K_STAT10_FROM_STAT1;

        // 부스탯10은 보수적으로 stat10 일부로 시작
        double sub10 = stat10 * 0.10;

        return new EfficiencyResponse(
                c.characterName(),
                c.characterClass(),
                baseR,
                bossDef,
                boss1,
                atk1,
                stat1,
                atk10,
                fd1,
                ied1,
                cdmg1,
                all1,
                cooldown1,
                sub10,
                stat10
        );
    }

    private double power(Snapshot s) {
        double atkFactor = 1.0 + s.atkPct / 100.0;
        double statFactor = 1.0 + s.statPct / 100.0;
        double dmgFactor = 1.0 + (s.damagePct + s.bossPct) / 100.0;
        double cdmgFactor = 1.0 + s.critDmgPct / 100.0;
        double fdFactor = 1.0 + s.finalDmgPct / 100.0;

        // 방무(보스 방어율 기준)
        double ied = clamp(s.iedPct / 100.0, 0.0, 0.999999);
        double def = bossDef / 100.0;
        double iedFactor = 1.0 - def * (1.0 - ied);
        if (iedFactor < 1e-9) iedFactor = 1e-9;

        return atkFactor * statFactor * dmgFactor * cdmgFactor * fdFactor * iedFactor;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private record Snapshot(
            double finalDmgPct,
            double bossPct,
            double damagePct,
            double iedPct,
            double critDmgPct,
            double statPct,
            double atkPct
    ) {
        static Snapshot from(CharacterCalcInput c, DetailStatResponse d, boolean isMage, boolean applyDoping) {
            // 올스탯%를 주스탯%로 합산
            double statPct = safe(c.mainStatPct()) + safe(c.allStatPct());

            double atkPct = isMage ? safe(c.magicPct()) : safe(c.attackPct());

            double boss = safe(c.bossDamagePct());
            double dmg  = safe(c.damagePct());
            double cdmg = safe(c.critDamagePct());

            // ✅ 넥슨 API는 도핑 미포함 → applyDoping이면 DopingConstants로 더함
            if (applyDoping) {
                atkPct += DopingConstants.ATK_PCT;          // :contentReference[oaicite:1]{index=1}
                boss  += DopingConstants.BOSS_PCT;         // :contentReference[oaicite:2]{index=2}
                dmg   += DopingConstants.DAMAGE_PCT;       // :contentReference[oaicite:3]{index=3}
                cdmg  += DopingConstants.CRIT_DMG_PCT;     // :contentReference[oaicite:4]{index=4}
                // ATK_FLAT(+305)은 현재 power()가 % 위주 모델이라 여기서는 직접 사용 안 함
                // (원하면 later에 공10 계산을 더 현실화할 때 반영 가능)
            }

            return new Snapshot(
                    safe(c.finalDamagePct()),
                    boss,
                    dmg,
                    safe(c.ignoreDefensePct()),
                    cdmg,
                    statPct,
                    atkPct
            );
        }

        Snapshot withFinalDmgPct(double v) { return new Snapshot(v, bossPct, damagePct, iedPct, critDmgPct, statPct, atkPct); }
        Snapshot withBossPct(double v)     { return new Snapshot(finalDmgPct, v, damagePct, iedPct, critDmgPct, statPct, atkPct); }
        Snapshot withAtkPct(double v)      { return new Snapshot(finalDmgPct, bossPct, damagePct, iedPct, critDmgPct, statPct, v); }
        Snapshot withStatPct(double v)     { return new Snapshot(finalDmgPct, bossPct, damagePct, iedPct, critDmgPct, v, atkPct); }
        Snapshot withIedPct(double v)      { return new Snapshot(finalDmgPct, bossPct, damagePct, v, critDmgPct, statPct, atkPct); }
        Snapshot withCritDmgPct(double v)  { return new Snapshot(finalDmgPct, bossPct, damagePct, iedPct, v, statPct, atkPct); }
    }

    private static double safe(Double v) {
        return v == null ? 0.0 : v;
    }
    public double calcActualGainForNode(CharacterCalcInput c, DetailStatResponse d, int baseR, boolean isMage,
                                        double dBoss, double dAtk, double dStat, double dFd, double dIed,
                                        double dCdmg, double dAllstat, int dCooldownSec) {

        Snapshot base = Snapshot.from(c, d, isMage, true);
        double p0 = power(base);

        // 규칙: 올스탯%는 주스탯%로 합산, 쿨감초는 주스탯%로 치환
        double statDelta = dStat + dAllstat + (dCooldownSec * COOLDOWN_TO_STAT_PCT);

        Snapshot s1 = base
                .withBossPct(base.bossPct + dBoss)
                .withAtkPct(base.atkPct + dAtk)
                .withStatPct(base.statPct + statDelta)
                .withFinalDmgPct(base.finalDmgPct + dFd)
                .withIedPct(base.iedPct + dIed)
                .withCritDmgPct(base.critDmgPct + dCdmg);

        double rel = (power(s1) - p0) / p0;
        return baseR * rel;
    }

}
