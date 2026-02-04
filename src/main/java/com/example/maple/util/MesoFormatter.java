package com.example.maple.util;

public class MesoFormatter {
    public static String format(long meso) {
        if (meso == 0)
            return "0메소";

        long eok = meso / 100_000_000L;
        long man = (meso % 100_000_000L) / 10_000L;

        StringBuilder sb = new StringBuilder();
        if (eok > 0) {
            sb.append(eok).append("억");
        }
        if (man > 0) {
            if (eok > 0)
                sb.append(" ");
            sb.append(man).append("만");
        }

        if (sb.length() == 0) {
            return meso + " 메소";
        }

        sb.append(" 메소");
        return sb.toString();
    }
}
