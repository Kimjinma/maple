package com.example.maple.util;

import com.example.maple.dto.item.ItemOptionSummaryResponse;
import java.util.List;
import java.util.Map;

public class SlotAliasUtil {

    public static ItemOptionSummaryResponse.SlotOption findCurrentItem(
            Map<String, ItemOptionSummaryResponse.SlotOption> currentItems, String slotKey) {
        if (currentItems.containsKey(slotKey))
            return currentItems.get(slotKey);

        String normalized = slotKey.replace(" ", "").trim();
        if (currentItems.containsKey(normalized))
            return currentItems.get(normalized);

        List<String> aliases = getSlotAliases(normalized);
        for (String alias : aliases) {
            String nAlias = alias.replace(" ", "");
            if (currentItems.containsKey(nAlias))
                return currentItems.get(nAlias);
        }
        return null;
    }

    public static List<String> getSlotAliases(String slot) {
        if (slot.equals("반지"))
            return List.of("반지1", "반지2", "반지3", "반지4");
        if (slot.equals("펜던트") || slot.equals("펜던트1"))
            return List.of("펜던트", "펜던트2");

        return List.of();
    }
}
