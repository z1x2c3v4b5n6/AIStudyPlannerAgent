package com.yhk.aistudyplanner.assessment;

import java.text.Normalizer;
import java.util.Locale;

public final class AssessmentTopics {
    private AssessmentTopics() {}

    public static boolean related(String pathTopic,String unitTitle) {
        String path=key(pathTopic);
        String unit=key(unitTitle);
        return !path.isEmpty() && !unit.isEmpty() && (path.equals(unit) || path.length()>=4 && unit.length()>=4
                && (path.contains(unit) || unit.contains(path)));
    }

    private static String key(String value) {
        return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }
}
