package com.example.maple.service;

import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.eff.EfficiencyResponse;
import com.example.maple.dto.stat.DetailStatResponse;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Consumer;

@Component
public class EfficiencyCalculatorV3 {

    private double bossDefRatio = 3.8; // default 380 (Kalos/Kaling standard)

    @Autowired
    public EfficiencyCalculatorV3() {
    }

    public EfficiencyCalculatorV3(int bossDef) {
        this.bossDefRatio = bossDef / 100.0;
    }

    public EfficiencyResponse calc(CharacterCalcInput c, DetailStatResponse d, int baseR, boolean isMage) {

        Snapshot s0 = Snapshot.from(c, d, isMage);
        Weights w = Weights.fromBaseR(baseR);

        double atk1 = deltaR(baseR, w.k, s0, s -> s.atkPct += 1.0, w);
        double boss1 = deltaR(baseR, w.k, s0, s -> s.bossDamagePct += 1.0, w);
        double stat1 = deltaR(baseR, w.k, s0, s -> s.mainStatPctOnly += 1.0, w);
        double all1 = deltaR(baseR, w.k, s0, s -> s.allStatPct += 1.0, w);
        double fd1 = deltaR(baseR, w.k, s0, s -> s.finalDamagePct += 1.0, w);
        double cdmg1 = deltaR(baseR, w.k, s0, s -> s.critDamagePct += 1.0, w);

        double ied1 = calcIed1(baseR, w.k, s0.ignoreDefensePct, w.wIed);

        double atk10 = atk1 * w.rAtk10;
        double stat10 = stat1 * w.rStat10;
        double sub10 = stat1 * w.rSub10;

        double cooldown1 = 0;

        return new EfficiencyResponse(
                c.characterName(),
                c.characterClass(),
                baseR,
                (int) Math.round(bossDefRatio * 100),
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
                stat10);
    }

    public double calcActualGainForNode(
            CharacterCalcInput c,
            DetailStatResponse d,
            int baseR,
            boolean isMage,
            double dBossPct,
            double dAtkPct,
            double dStatPct,
            double dDmgPct,
            double dFinalDmgPct,
            double dIedPct,
            double dCdmgPct,
            double dAllstatPct,
            int dCooldownSec,
            double dMainFlat,
            double dAtkFlat) {
        Snapshot s0 = Snapshot.from(c, d, isMage);
        Weights w = Weights.fromBaseR(baseR);

        double gainR = deltaR(baseR, w.k, s0, s -> {
            s.bossDamagePct += dBossPct;
            s.atkPct += dAtkPct;
            s.mainStatPctOnly += dStatPct;
            s.damagePct += dDmgPct;
            s.allStatPct += dAllstatPct;
            s.finalDamagePct += dFinalDmgPct;
            s.critDamagePct += dCdmgPct;

            s.mainStatValue += dMainFlat;
            s.attackValue += dAtkFlat;
        }, w);

        if (Math.abs(dIedPct) > 1e-12) {
            double iedBefore = clamp01(s0.ignoreDefensePct / 100.0);

            double effectiveDelta = (dIedPct / 100.0) * (1.0 - iedBefore);
            double iedAfter = clamp01(iedBefore + effectiveDelta);

            double iedGain = iedCurveGain(iedBefore, iedAfter);
            gainR += baseR * w.k * iedGain * w.wIed;
        }

        return gainR;
    }

    double wAtk = 1.0;
    double wBoss = 1.0;
    double wStat = 1.0;
    double wAll = 1.15;
    double wCdmg = 1.0;
    double wIed = 1.0;

    private double deltaR(int baseR, double k, Snapshot s0, Consumer<Snapshot> apply, Weights w) {
        double p0 = powerNoIed(s0, w);

        Snapshot s1 = s0.copy();
        apply.accept(s1);

        double p1 = powerNoIed(s1, w);

        double gain = (p1 / p0) - 1.0;
        if (Double.isNaN(gain) || Double.isInfinite(gain) || gain < -1.0) {

        }
        return baseR * k * gain;
    }

    private double powerNoIed(Snapshot s, Weights w) {
        // Floor stats to prevent division by zero or negative power
        double main = Math.max(10.0, s.mainStatValue);
        double sub = Math.max(1.0, s.subStatValue);
        double atk = Math.max(10.0, s.attackValue);

        // 보공 가중 (최소 1.0)
        double D = Math.max(1.0, 1.0 + (s.damagePct + s.bossDamagePct * w.wBoss) / 100.0);

        // 최종뎀 배율 (최소 1.0)
        double F = Math.max(1.0, 1.0 + s.finalDamagePct / 100.0);

        // 주스탯/올스탯 분리
        double statPctTotal = s.mainStatPctOnly + s.allStatPct * w.wAll;

        double A = atk * (1.0 + (s.atkPct / 100.0) * w.wAtk);
        double S = (main + 0.10 * sub) * (1.0 + (statPctTotal / 100.0) * w.wStat);

        double C = 1.0 + (s.critDamagePct / 100.0) * w.wCdmg;

        return (A * S * D * F * C) / 1e12;
    }

    private double calcIed1(int baseR, double k, double iedPct, double wIed) {
        // [FIX] "1% IED" option behaves multiplicatively, not additively.
        // New IED = 1 - (1 - Current) * (1 - 0.01)
        double i0 = clamp01(iedPct / 100.0);
        double i1 = 1.0 - (1.0 - i0) * 0.99;

        double gain = iedCurveGain(i0, i1);
        return baseR * k * gain * wIed;
    }

    private double iedCurveGain(double ied0, double ied1) {
        double m0 = 1.0 - bossDefRatio * (1.0 - ied0);
        double m1 = 1.0 - bossDefRatio * (1.0 - ied1);

        // If the character does zero damage due to low IED, use a small floor (0.01)
        // instead of 1e-6
        // to prevent astronomical gains while still acknowledging IED is valuable.
        if (m0 < 0.01)
            m0 = 0.01;
        if (m1 < 0.01)
            m1 = 0.01;

        return (m1 / m0) - 1.0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static final class Weights {
        final double k;

        final double wAtk;
        final double wStat;
        final double wAll; //
        final double wCdmg;
        final double wBoss;
        final double wIed;

        final double rAtk10;
        final double rStat10;
        final double rSub10;

        private Weights(
                double k,
                double wAtk, double wStat, double wAll,
                double wCdmg, double wBoss, double wIed,
                double rAtk10, double rStat10, double rSub10) {
            this.k = k;
            this.wAtk = wAtk;
            this.wStat = wStat;
            this.wAll = wAll;
            this.wCdmg = wCdmg;
            this.wBoss = wBoss;
            this.wIed = wIed;
            this.rAtk10 = rAtk10;
            this.rStat10 = rStat10;
            this.rSub10 = rSub10;
        }

        static Weights fromBaseR(int baseR) {

            double rr = Math.max(1000.0, baseR);

            double k = 0.35;

            double wAtk = 1.0;
            double wBoss = 1.0;
            double wStat = 1.0;
            double wAll = 1.15;
            double wCdmg = 1.0;
            double wIed = 1.0;

            double rAtk10 = 0.77;
            double rStat10 = 0.81;
            double rSub10 = 0.067;

            return new Weights(k, wAtk, wStat, wAll, wCdmg, wBoss, wIed, rAtk10, rStat10, rSub10);
        }
    }

    private static final class Snapshot {
        double mainStatValue;
        double subStatValue;
        double attackValue;

        double damagePct;
        double bossDamagePct;
        double finalDamagePct;
        double ignoreDefensePct;
        double critDamagePct;

        double mainStatPctOnly;
        double allStatPct;
        double atkPct;

        static Snapshot from(CharacterCalcInput c, DetailStatResponse d, boolean isMage) {
            Snapshot s = new Snapshot();

            s.mainStatValue = d.mainStat();
            s.subStatValue = d.subStat();
            s.attackValue = isMage ? d.magicPower() : d.attackPower();

            s.damagePct = c.damagePct();
            s.bossDamagePct = c.bossDamagePct();
            s.finalDamagePct = c.finalDamagePct();
            s.ignoreDefensePct = c.ignoreDefensePct();
            s.critDamagePct = c.critDamagePct();

            s.mainStatPctOnly = c.mainStatPct();
            s.allStatPct = c.allStatPct();

            s.atkPct = isMage ? c.magicPct() : c.attackPct();
            return s;
        }

        Snapshot copy() {
            Snapshot t = new Snapshot();
            t.mainStatValue = this.mainStatValue;
            t.subStatValue = this.subStatValue;
            t.attackValue = this.attackValue;

            t.damagePct = this.damagePct;
            t.bossDamagePct = this.bossDamagePct;
            t.finalDamagePct = this.finalDamagePct;
            t.ignoreDefensePct = this.ignoreDefensePct;
            t.critDamagePct = this.critDamagePct;

            t.mainStatPctOnly = this.mainStatPctOnly;
            t.allStatPct = this.allStatPct;
            t.atkPct = this.atkPct;
            return t;
        }
    }
}
