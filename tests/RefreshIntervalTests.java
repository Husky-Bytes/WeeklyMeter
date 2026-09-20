package dev.yerin.weeklymeter;

public final class RefreshIntervalTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int value:new int[]{15,16,20,29,30,31,45,47,60,61,90,120,1440,4320,10079,10080}){
            check(RefreshInterval.valid(value),"valid minutes");
            check(RefreshInterval.parse(String.valueOf(value))==value,"custom input preserved");
            check(RefreshInterval.normalize(value)==value,"saved custom interval preserved");
            check(RefreshInterval.millis(value)==value*60_000L,"millisecond conversion");
        }
        for(int value:new int[]{Integer.MIN_VALUE,-1,0,1,14,10081,Integer.MAX_VALUE}){
            check(!RefreshInterval.valid(value),"invalid minutes");
            check(RefreshInterval.normalize(value)==15,"invalid persisted value falls back");
            check(RefreshInterval.millis(value)==900000,"invalid value cannot overflow or create rapid job");
        }
        for(String value:new String[]{null,""," ","14","0","-15","+15","15.0","15,0","1e3","15 minutes","15분","10081","999999999999999999999999999","1 5","15\n30","NaN","Infinity"})
            check(RefreshInterval.parse(value)==-1,"reject malformed/range input: "+value);
        check(RefreshInterval.parse(" 47 \n")==47,"surrounding whitespace");
        check(RefreshInterval.parse("00015")==15,"leading zeros");
        check(RefreshInterval.parse("４７")==47,"fullwidth digits");
        check(RefreshInterval.parse("٤٧")==47,"Arabic digits");
        System.out.println("PASS: "+checks+" refresh interval input/range/conversion checks (pure Java)");
    }
}
