package dev.yerin.weeklymeter;

import java.net.URI;

/** All native app network requests MUST pass this exact URL+method allowlist. */
public final class NetworkPolicy {
    public static final String AUTH="https://auth.openai.com";
    public static final String USAGE="https://chatgpt.com/backend-api/wham/usage";
    public static void check(String method,String address,boolean bearer) {
        URI u=URI.create(address);
        if(!"https".equals(u.getScheme()) || u.getRawUserInfo()!=null || u.getPort()!=-1 || u.getRawQuery()!=null || u.getRawFragment()!=null)
            throw new SecurityException("허용되지 않은 통신 주소");
        boolean auth=address.equals(AUTH+"/oauth/token");
        if(auth && "POST".equals(method) && !bearer)return;
        if(address.equals(USAGE) && "GET".equals(method) && bearer)return;
        throw new SecurityException("허용되지 않은 통신 경로");
    }
    public static boolean headerSafe(String value){return value!=null&&!value.isEmpty()&&value.length()<32768&&value.chars().allMatch(c->c>=33&&c<=126);}
    private NetworkPolicy(){}
}
