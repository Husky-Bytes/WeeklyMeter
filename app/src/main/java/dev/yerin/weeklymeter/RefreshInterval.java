package dev.yerin.weeklymeter;

/** Periodic background refresh policy. Changing it never performs a usage request. */
final class RefreshInterval {
    static final int MIN_MINUTES=15, MAX_MINUTES=10080, DEFAULT_MINUTES=15;
    static boolean valid(int minutes){return minutes>=MIN_MINUTES&&minutes<=MAX_MINUTES;}
    static int normalize(int minutes){return valid(minutes)?minutes:DEFAULT_MINUTES;}
    /** Returns -1 for invalid input; never silently changes what the user entered. */
    static int parse(String input){
        if(input==null)return -1;
        String value=input.trim();if(value.isEmpty()||value.length()>5)return -1;
        int minutes=0;
        for(int i=0;i<value.length();i++){
            int digit=Character.digit(value.charAt(i),10);if(digit<0)return -1;
            minutes=minutes*10+digit;
        }
        return valid(minutes)?minutes:-1;
    }
    static long millis(int minutes){return normalize(minutes)*60_000L;}
    private RefreshInterval(){}
}
