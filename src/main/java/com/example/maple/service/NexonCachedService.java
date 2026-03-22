package com.example.maple.service;

import com.example.maple.client.NexonApiClient;
import com.example.maple.dto.CharacterBasicResponse; // ✅ 추가
import com.example.maple.dto.item.ItemStat;
import com.example.maple.dto.ocid.OcidResponse;
import com.example.maple.dto.stat.CharacterStatResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class NexonCachedService {

    private final NexonApiClient nexonApiClient;

    public NexonCachedService(NexonApiClient nexonApiClient) {
        this.nexonApiClient = nexonApiClient;
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name이 비었습니다.");
        }
        return name.trim();
    }

    @Cacheable(cacheNames = "ocid", key = "#name")
    public String getOcid(String name) {
        String n = normalizeName(name);
        OcidResponse res = nexonApiClient.getOcid(n);
        if (res == null || res.ocid() == null) {
            throw new IllegalArgumentException("ocid 조회 실패: " + n);
        }
        return res.ocid();
    }

    @Cacheable(cacheNames = "basic", key = "#ocid")
    public CharacterBasicResponse getBasic(String ocid) {
        CharacterBasicResponse basic = nexonApiClient.getBasic(ocid);
        if (basic == null) {
            throw new IllegalArgumentException("기본정보 조회 실패: " + ocid);
        }
        return basic;
    }

    @Cacheable(cacheNames = "stat", key = "#ocid")
    public CharacterStatResponse getStat(String ocid) {
        CharacterStatResponse stat = nexonApiClient.getStat(ocid);
        if (stat == null || stat.finalStat() == null) {
            throw new IllegalArgumentException("스탯 조회 실패: " + ocid);
        }
        return stat;
    }

    @Cacheable(cacheNames = "item", key = "#ocid")
    public ItemStat getItem(String ocid) {
        ItemStat item = nexonApiClient.getItemStat(ocid);
        if (item == null || item.itemequipment() == null) {
            throw new IllegalArgumentException("아이템 조회 실패: " + ocid);
        }
        return item;
    }

    @Cacheable(cacheNames = "setEffect", key = "#ocid")
    public com.example.maple.dto.seteffect.SetEffectResponse getSetEffect(String ocid) {
        com.example.maple.dto.seteffect.SetEffectResponse all = nexonApiClient.getSetEffect(ocid);
        if (all == null || all.setEffect() == null) {

            return all;
        }
        return all;
    }
}
