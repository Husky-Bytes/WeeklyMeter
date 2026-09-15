package dev.yerin.weeklymeter;

import java.util.Locale;

public final class LanguagePolicyTests {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    public static void main(String[] args){
        equal("system",LanguagePolicy.normalize(null));equal("system",LanguagePolicy.normalize("fr"));
        equal("ko",LanguagePolicy.normalize("ko"));equal("en",LanguagePolicy.normalize("en"));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",null));
        equal(Locale.KOREAN,LanguagePolicy.resolve("system",Locale.KOREAN));
        equal(Locale.KOREAN,LanguagePolicy.resolve("system",Locale.KOREA));
        equal(Locale.KOREAN,LanguagePolicy.resolve("system",Locale.forLanguageTag("ko-US")));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",Locale.ENGLISH));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",Locale.forLanguageTag("en-KR")));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",Locale.JAPANESE));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",Locale.FRENCH));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",Locale.forLanguageTag("ar-SA")));
        equal(Locale.KOREAN,LanguagePolicy.resolve("ko",Locale.FRENCH));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("en",Locale.KOREAN));
        equal(Locale.ENGLISH,LanguagePolicy.resolve("invalid",Locale.JAPANESE));
        equal("system",LanguagePolicy.platformChoice(null));equal("system",LanguagePolicy.platformChoice(""));
        equal("ko",LanguagePolicy.platformChoice("ko-KR"));equal("ko",LanguagePolicy.platformChoice("ko,en"));
        equal("en",LanguagePolicy.platformChoice("en,ko"));equal("en",LanguagePolicy.platformChoice("fr,ko"));
        equal("en",LanguagePolicy.platformChoice("ja-JP"));equal("en",LanguagePolicy.platformChoice("invalid"));
        // Region and secondary language must never turn a non-Korean primary language Korean.
        Locale[] multilingual={Locale.FRENCH,Locale.KOREAN};
        equal(Locale.ENGLISH,LanguagePolicy.resolve("system",multilingual[0]));
        for(Locale system:new Locale[]{Locale.KOREA,Locale.KOREAN,Locale.ENGLISH,Locale.FRENCH,Locale.JAPANESE,Locale.CHINESE,Locale.ROOT}){
            equal(Locale.KOREAN,LanguagePolicy.resolve("ko",system));equal(Locale.ENGLISH,LanguagePolicy.resolve("en",system));
        }
        System.out.println("LanguagePolicyTests: "+checks+" checks passed");
    }
}
