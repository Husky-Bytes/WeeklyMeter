package dev.yerin.weeklymeter;
import java.util.*;
public final class CoreTests {
    static int count;
    static void check(boolean condition,String name){count++;if(!condition)throw new AssertionError(name);}
    static void rejects(Runnable r,String name){count++;try{r.run();}catch(IllegalArgumentException|SecurityException e){return;}throw new AssertionError("Must reject: "+name);}
    static String raw(Object duration,Object percent,Object reset){return Json.encode(Json.map("rate_limit",Json.map("secondary_window",Json.map("limit_window_seconds",duration,"used_percent",percent,"reset_at",reset))));}
    public static void main(String[] args){
        long now=1789390000000L;
        Usage u=Usage.parse(raw(604800,27,1789990000L),now).get(0);
        check(u.percent().equals("73%"),"27 used => 73 remaining");
        check(u.id.equals("codex"),"main bucket identity");
        check(!u.expired(now),"before reset");
        check(u.expired(1789990000000L),"exact reset boundary");
        check(Usage.parse(raw(604800,100,1789990000L),now).get(0).percent().equals("0%"),"zero remaining");
        check(Usage.parse(raw(604800,0,1789990000L),now).get(0).percent().equals("100%"),"full remaining");
        check(Usage.parse(raw(604800,27.4,1789990000L),now).get(0).percent().equals("72.6%"),"decimal percent");
        rejects(()->Usage.parse(raw(18000,27,1789990000L),now),"five-hour is not weekly");
        rejects(()->Usage.parse(raw(86400,27,1789990000L),now),"daily is not weekly");
        rejects(()->Usage.parse(raw(null,27,1789990000L),now),"unknown duration is not weekly");
        rejects(()->Usage.parse(raw(604800,null,1789990000L),now),"unknown usage is not 0");
        rejects(()->Usage.parse(raw(604800,-1,1789990000L),now),"negative percent");
        rejects(()->Usage.parse(raw(604800,101,1789990000L),now),"overrange percent");
        rejects(()->Usage.parse(raw(604800,"27",1789990000L),now),"unexpected numeric string");
        rejects(()->Usage.parse(raw(604800,27,1789990000000L),now),"milliseconds mistaken for seconds");
        rejects(()->Usage.parse(raw(604800,27,"tomorrow"),now),"invalid reset string");
        check(Usage.parse(raw(604800,27,null),now).get(0).resetsAt==0,"missing reset stays unknown");
        String primary=Json.encode(Json.map("rate_limit",Json.map("primary_window",Json.map("limit_window_seconds",604800,"used_percent",15))));
        check(Usage.parse(primary,now).get(0).percent().equals("85%"),"weekly can be primary");
        String multi=Json.encode(Json.map("rate_limit",Json.map("secondary_window",Json.map("limit_window_seconds",604800,"used_percent",27)),"additional_rate_limits",Arrays.asList(Json.map("metered_feature","extra","limit_name","추가 한도","rate_limit",Json.map("primary_window",Json.map("limit_window_seconds",604800,"used_percent",10))))));
        check(Usage.parse(multi,now).size()==2,"multiple weekly buckets");
        check(Usage.parse(multi,now).get(1).label.equals("추가 한도"),"bucket label preserved");
        String rpc="{\"result\":{\"rateLimitsByLimitId\":{\"codex\":{\"secondary\":{\"windowDurationMins\":10080,\"usedPercent\":50,\"resetsAt\":1789990000}}}}}";
        check(Usage.parse(rpc,now).get(0).percent().equals("50%"),"documented App Server shape");
        rejects(()->Usage.parse("{}",now),"empty response");
        rejects(()->Usage.parse("[]",now),"wrong root");
        rejects(()->Json.parse("{\"x\":1,\"x\":2}"),"duplicate keys");
        rejects(()->Json.parse("[1,]"),"trailing comma");
        rejects(()->Json.parse("true false"),"trailing garbage");
        rejects(()->Json.parse("01"),"leading zero");
        rejects(()->Json.parse("1e999"),"infinite number");
        rejects(()->Json.parse("{\"x\":\"\\uZZZZ\"}"),"invalid unicode escape");
        String text="예린\n\"a\\b\" 😀";
        check(Json.parse(Json.encode(text)).equals(text),"Unicode/escaping round trip");
        check(Json.encode(Json.parse(multi)).equals(multi),"structural round trip");
        String deep="0";for(int i=0;i<40;i++)deep="["+deep+"]";final String tooDeep=deep;
        rejects(()->Json.parse(tooDeep),"depth cap");
        check(Usage.fromMap(Json.object(Json.parse(Json.encode(u.toMap())))).percent().equals("73%"),"cache round trip");
        NetworkPolicy.check("GET",NetworkPolicy.USAGE,true);count++;
        NetworkPolicy.check("POST",NetworkPolicy.AUTH+"/oauth/token",false);count++;
        rejects(()->NetworkPolicy.check("POST",NetworkPolicy.AUTH+"/api/accounts/deviceauth/usercode",false),"device code route removed");
        rejects(()->NetworkPolicy.check("POST",NetworkPolicy.AUTH+"/api/accounts/deviceauth/token",false),"device polling route removed");
        rejects(()->NetworkPolicy.check("GET","https://evil.example/",true),"external host");
        rejects(()->NetworkPolicy.check("GET","https://chatgpt.com.evil.example/backend-api/wham/usage",true),"host suffix trap");
        rejects(()->NetworkPolicy.check("GET","https://chatgpt.com@evil.example/backend-api/wham/usage",true),"userinfo trap");
        rejects(()->NetworkPolicy.check("GET","http://chatgpt.com/backend-api/wham/usage",true),"cleartext");
        rejects(()->NetworkPolicy.check("GET",NetworkPolicy.USAGE+"?redirect=evil",true),"query injection");
        rejects(()->NetworkPolicy.check("POST",NetworkPolicy.USAGE,true),"usage write disallowed");
        rejects(()->NetworkPolicy.check("POST","https://chatgpt.com/backend-api/responses",true),"generation disallowed");
        rejects(()->NetworkPolicy.check("POST",NetworkPolicy.AUTH+"/oauth/token",true),"bearer to auth disallowed");
        rejects(()->NetworkPolicy.check("GET","https://chatgpt.com:443/backend-api/wham/usage",true),"unexpected port");
        check(!NetworkPolicy.headerSafe("a\r\nAuthorization: x"),"header injection");
        check(!NetworkPolicy.headerSafe(""),"empty auth");
        check(NetworkPolicy.headerSafe("a-b.c_123"),"normal token character set");
        System.out.println("PASS: "+count+" core checks. Android build, device login, and live API NOT tested.");
    }
}
