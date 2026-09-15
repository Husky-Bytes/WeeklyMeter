package dev.yerin.weeklymeter;

import java.util.*;

/** Independent synthetic-data regression suite; no Android, network, or credentials. */
public final class UsageRegressionTests {
    private static final long NOW=1789390000000L, RESET=1789990000L, WEEK_MS=604800000L;
    private static int checks,failures;
    private static void test(String name,Runnable body) {
        checks++;
        try {body.run();}
        catch(Throwable failure) {failures++;System.out.println("FAIL: "+name+" — "+failure);}
    }
    private static void require(boolean value) {if(!value)throw new AssertionError("condition false");}
    private static void rejects(Runnable body) {
        try {body.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("invalid/ambiguous data was accepted");
    }
    private static Map<String,Object> week() {return Json.map("limit_window_seconds",604800,"used_percent",27,"reset_at",RESET);}
    private static Map<String,Object> camelWeek() {return Json.map("windowDurationMins",10080,"usedPercent",27,"resetsAt",RESET);}
    private static Map<String,Object> rawBucket() {return Json.map("secondary_window",week());}
    private static Map<String,Object> rawRoot() {return Json.map("rate_limit",rawBucket());}
    private static Map<String,Object> camelBucket() {return Json.map("secondary",camelWeek());}
    private static List<Usage> parse(Map<String,Object> root) {return Usage.parse(Json.encode(root),NOW);}
    private static Map<String,Object> cache() {return new Usage("codex","기본",27,RESET,NOW).toMap();}
    private static Map<String,Object> with(Map<String,Object> base,String key,Object value) {base.put(key,value);return base;}
    private static void rejectWindowField(String name,String key,Object value) {
        test(name,()->rejects(()->parse(Json.map("rate_limit",Json.map("secondary_window",with(week(),key,value))))));
    }
    private static void rejectCacheField(String name,String key,Object value) {
        test(name,()->rejects(()->Usage.fromMap(with(cache(),key,value))));
    }
    public static void main(String[] args) {
        test("only exact weekly window, not five-hour primary",()->{
            Map<String,Object> b=rawBucket();b.put("primary_window",Json.map("limit_window_seconds",18000,"used_percent",90));
            require(parse(Json.map("rate_limit",b)).get(0).percent().equals("73%"));
        });
        test("1001 samples retain the mathematical remaining percentage",()->{
            for(int tenth=0;tenth<=1000;tenth++) {
                double used=tenth/10.0;
                Usage u=parse(Json.map("rate_limit",Json.map("secondary_window",with(week(),"used_percent",used)))).get(0);
                require(Math.abs(u.remaining()-(100.0-used))<1e-9);
                require(Math.abs(Double.parseDouble(u.percent().replace("%",""))-u.remaining())<0.051);
            }
        });
        test("decimal rounding is to one decimal place",()->{
            require(new Usage("x","x",27.44,RESET,NOW).percent().equals("72.6%"));
            require(new Usage("x","x",27.46,RESET,NOW).percent().equals("72.5%"));
        });
        test("percentage output independent of device decimal separator",()->{
            Locale previous=Locale.getDefault();
            try{Locale.setDefault(Locale.GERMANY);require(new Usage("x","x",27.4,RESET,NOW).percent().equals("72.6%"));}
            finally{Locale.setDefault(previous);}
        });
        for(Object duration:Arrays.asList(604799,604801,604800.5,"604800",true,null))
            rejectWindowField("invalid weekly duration "+duration,"limit_window_seconds",duration);
        for(Object used:Arrays.asList(-0.01,100.01,"27",true,null))
            rejectWindowField("invalid percentage "+used,"used_percent",used);
        for(Object reset:Arrays.asList(-1,0,RESET*1000L,"1789990000",true,RESET+0.5))
            rejectWindowField("invalid reset "+reset,"reset_at",reset);
        test("null reset is explicitly unknown at fetch",()->require(parse(Json.map("rate_limit",Json.map("secondary_window",with(week(),"reset_at",null)))).get(0).resetsAt==0));
        test("reset boundary expires at exact second",()->{
            Usage u=parse(rawRoot()).get(0);require(!u.expired(RESET*1000L-1));require(u.expired(RESET*1000L));
        });
        test("already reset server response never displayed as current",()->require(new Usage("x","x",27,NOW/1000-1,NOW).expired(NOW)));
        test("unknown reset snapshot expires at seven days",()->{
            Usage u=new Usage("x","x",27,0,NOW);require(!u.expired(NOW+WEEK_MS-1));require(u.expired(NOW+WEEK_MS));
        });
        test("far future reset cannot preserve old weekly data forever",()->require(new Usage("x","x",27,RESET+604800*10L,NOW).expired(NOW+WEEK_MS)));
        test("clock rollback invalidates apparently future cache",()->require(new Usage("x","x",27,RESET,NOW).expired(NOW-1)));
        test("valid cache round trip preserves all numeric data",()->{
            Usage u=Usage.fromMap(Json.object(Json.parse(Json.encode(cache()))));
            require(u.used==27&&u.resetsAt==RESET&&u.fetchedAt==NOW&&u.id.equals("codex"));
        });
        for(Object value:Arrays.asList("bad",null,RESET+0.5,RESET*1000L))
            rejectCacheField("corrupt cached reset "+value,"reset",value);
        for(Object value:Arrays.asList("bad",null,NOW+0.5))
            rejectCacheField("corrupt cached fetch timestamp "+value,"fetched",value);
        test("missing cached reset is rejected",()->{Map<String,Object> m=cache();m.remove("reset");rejects(()->Usage.fromMap(m));});
        test("missing cached fetch timestamp is rejected",()->{Map<String,Object> m=cache();m.remove("fetched");rejects(()->Usage.fromMap(m));});
        rejectCacheField("missing cached percentage is not zero","used",null);
        test("multiple raw weekly buckets retain distinct identities",()->{
            Map<String,Object> r=rawRoot();r.put("additional_rate_limits",Arrays.asList(Json.map("metered_feature","spark","limit_name","Spark","rate_limit",rawBucket())));
            List<Usage> list=parse(r);require(list.size()==2&&list.get(0).id.equals("codex")&&list.get(1).id.equals("spark"));
        });
        test("duplicate raw bucket IDs rejected",()->rejects(()->parse(with(rawRoot(),"additional_rate_limits",Arrays.asList(Json.map("metered_feature","codex","rate_limit",rawBucket()))))));
        test("weekly additional bucket without identity rejected",()->rejects(()->parse(with(rawRoot(),"additional_rate_limits",Arrays.asList(Json.map("rate_limit",rawBucket()))))));
        test("nonweekly additional bucket without identity can be ignored",()->require(parse(with(rawRoot(),"additional_rate_limits",Arrays.asList(Json.map("rate_limit",Json.map("primary_window",Json.map("limit_window_seconds",18000,"used_percent",10)))))).size()==1));
        test("two weekly windows in one bucket rejected",()->rejects(()->parse(Json.map("rate_limit",with(rawBucket(),"primary_window",week())))));
        test("malformed additional bucket list rejected beside valid main",()->rejects(()->parse(with(rawRoot(),"additional_rate_limits",Json.map("new_schema",rawBucket())))));
        test("null item in additional bucket list rejected",()->rejects(()->parse(with(rawRoot(),"additional_rate_limits",Arrays.asList((Object)null)))));
        test("malformed recognized window rejected beside weekly window",()->rejects(()->parse(Json.map("rate_limit",with(rawBucket(),"primary_window","changed")))));
        test("malformed duration rejected beside weekly window",()->rejects(()->parse(Json.map("rate_limit",with(rawBucket(),"primary_window",Json.map("limit_window_seconds","18000"))))));
        test("null optional raw windows and additional list remain supported",()->require(parse(with(Json.map("rate_limit",with(rawBucket(),"primary_window",null)),"additional_rate_limits",null)).size()==1));
        test("authoritative RPC buckets preferred over different legacy value",()->{
            Map<String,Object> r=Json.map("rateLimitsByLimitId",Json.map("spark",camelBucket()),"rateLimits",Json.map("secondary",with(camelWeek(),"usedPercent",99)));
            List<Usage> list=parse(r);require(list.size()==1&&list.get(0).id.equals("spark")&&list.get(0).used==27);
        });
        test("empty authoritative map never falls back to legacy",()->rejects(()->parse(Json.map("rateLimitsByLimitId",Json.map(),"rateLimits",camelBucket()))));
        test("nonweekly authoritative map never falls back to legacy",()->rejects(()->parse(Json.map("rateLimitsByLimitId",Json.map("spark",Json.map("primary",Json.map("windowDurationMins",300,"usedPercent",10))),"rateLimits",camelBucket()))));
        test("malformed authoritative map never falls back to legacy",()->rejects(()->parse(Json.map("rateLimitsByLimitId","changed","rateLimits",camelBucket()))));
        test("missing authoritative map permits documented legacy",()->require(parse(Json.map("rateLimits",camelBucket())).get(0).percent().equals("73%")));
        test("null unavailable authoritative map permits documented legacy",()->require(parse(Json.map("rateLimitsByLimitId",null,"rateLimits",camelBucket())).get(0).percent().equals("73%")));
        test("explicit legacy bucket identity is never relabeled codex",()->{
            Usage u=parse(Json.map("rateLimits",with(with(camelBucket(),"limitId","spark"),"limitName","Spark 한도"))).get(0);
            require(u.id.equals("spark")&&u.label.equals("Spark 한도"));
        });
        test("unnamed legacy bucket uses its explicit ID as label",()->{
            Usage u=parse(Json.map("rateLimits",with(camelBucket(),"limitId","spark"))).get(0);
            require(u.id.equals("spark")&&u.label.equals("spark"));
        });
        test("RPC result wrapper accepted",()->require(parse(Json.map("result",Json.map("rateLimitsByLimitId",Json.map("codex",camelBucket())))).size()==1));
        test("two RPC weekly buckets retained",()->require(parse(Json.map("rateLimitsByLimitId",Json.map("codex",camelBucket(),"spark",camelBucket()))).size()==2));
        test("conflicting map key and declared bucket ID rejected",()->rejects(()->parse(Json.map("rateLimitsByLimitId",Json.map("spark",with(camelBucket(),"limitId","codex"))))));
        test("matching declared RPC bucket ID accepted",()->require(parse(Json.map("rateLimitsByLimitId",Json.map("spark",with(camelBucket(),"limitId","spark")))).get(0).id.equals("spark")));
        test("mixed incompatible response schemas rejected",()->rejects(()->parse(with(rawRoot(),"rateLimits",camelBucket()))));
        test("unrelated unknown fields do not change selected value",()->require(parse(with(rawRoot(),"future_feature",Json.map("value",50))).get(0).used==27));
        test("valid signed-long maximum retains exact value",()->require(Json.integer(Json.parse("9223372036854775807"),0)==Long.MAX_VALUE));
        test("large integer conversion never rounds through double",()->require(Json.integer(Json.parse("9007199254740993"),0)==9007199254740993L));
        test("valid signed-long minimum retains exact value",()->require(Json.integer(Json.parse("-9223372036854775808"),0)==Long.MIN_VALUE));
        test("signed Unicode plus escape is invalid JSON",()->rejects(()->Json.parse("\"\\u+000\"")));
        test("signed Unicode minus escape is invalid JSON",()->rejects(()->Json.parse("\"\\u-001\"")));
        test("non-ASCII Unicode escape digits invalid JSON",()->rejects(()->Json.parse("\"\\u\u0660\u0660\u0660\u0661\"")));
        test("valid Unicode escape and surrogate pair retained",()->require(Json.parse("\"\\uC608\\uB9B0 \\uD83D\\uDE00\"").equals("예린 😀")));
        test("escaped duplicate object keys rejected",()->rejects(()->Json.parse("{\"a\":1,\"\\u0061\":2}")));
        test("finite constructor range enforced independently of JSON",()->{
            for(double used:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.001,100.001})
                rejects(()->new Usage("x","x",used,RESET,NOW));
        });
        System.out.println("UsageRegressionTests: "+(checks-failures)+"/"+checks+" independent scenarios passed (including 1001 percentage samples); "+failures+" failed. Synthetic fixtures only; no live account or Android device exercised.");
        if(failures>0)throw new AssertionError(failures+" regression scenarios failed");
    }
}
