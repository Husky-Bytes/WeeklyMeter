package dev.yerin.weeklymeter;

import java.util.Locale;

/** Locale selection rules kept independent of Android for executable regression tests. */
final class LanguagePolicy {
    static final String SYSTEM="system", KO="ko", EN="en";
    static String normalize(String choice){return KO.equals(choice)?KO:EN.equals(choice)?EN:SYSTEM;}
    static Locale resolve(String choice,Locale primarySystemLocale){
        String selected=normalize(choice);
        if(SYSTEM.equals(selected))selected=primarySystemLocale!=null&&KO.equals(primarySystemLocale.getLanguage())?KO:EN;
        return KO.equals(selected)?Locale.KOREAN:Locale.ENGLISH;
    }
    static String platformChoice(String languageTags){
        if(languageTags==null||languageTags.isEmpty())return SYSTEM;
        return KO.equals(Locale.forLanguageTag(languageTags.split(",",2)[0]).getLanguage())?KO:EN;
    }
    private LanguagePolicy(){}
}
