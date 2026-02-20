package com.example.maple.client;

import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.item.ItemStat;
import com.example.maple.dto.ocid.OcidResponse;
import com.example.maple.dto.seteffect.SetEffectResponse;
import com.example.maple.dto.stat.CharacterStatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component

public class NexonApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NexonApiClient(RestTemplate nexonRestTemplate,
            @Value("${nexon.api.base-url}") String baseUrl) {
        this.restTemplate = nexonRestTemplate;
        this.baseUrl = baseUrl;
    }

    public OcidResponse getOcid(String characterName) {
        if (characterName == null || characterName.trim().isEmpty()) {
            throw new IllegalArgumentException("characterName이 비었습니다.");
        }
        characterName = characterName.trim();

        URI url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/maplestory/v1/id")
                .queryParam("character_name", characterName)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        return restTemplate.getForObject(url, OcidResponse.class);
    }

    public CharacterBasicResponse getBasic(String ocid) {
        ocid = ocid == null ? null : ocid.trim();

        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/maplestory/v1/character/basic")
                .queryParam("ocid", ocid)
                .build()
                .toUri();

        return restTemplate.getForObject(uri, CharacterBasicResponse.class);
    }

    public CharacterStatResponse getStat(String ocid) {

        ocid = ocid == null ? null : ocid.trim();

        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/maplestory/v1/character/stat")
                .queryParam("ocid", ocid);

        URI uri = b.build().toUri();
        return restTemplate.getForObject(uri, CharacterStatResponse.class);
    }

    public ItemStat getItemStat(String ocid) {
        ocid = ocid == null ? null : ocid.trim();
        UriComponentsBuilder c = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/maplestory/v1/character/item-equipment")
                .queryParam("ocid", ocid);
        URI uri = c.build().toUri();
        return restTemplate.getForObject(uri, ItemStat.class);
    }

    public SetEffectResponse getSetEffect(String ocid) {
        ocid = ocid == null ? null : ocid.trim();
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/maplestory/v1/character/set-effect")
                .queryParam("ocid", ocid)
                .build()
                .toUri();
        return restTemplate.getForObject(uri, SetEffectResponse.class);
    }
}
