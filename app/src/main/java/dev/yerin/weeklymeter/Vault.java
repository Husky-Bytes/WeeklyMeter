package dev.yerin.weeklymeter;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Called only on Repo.IO. Tokens never go to plain preferences. */
final class Vault {
    private static final String ALIAS="weeklymeter.session.v1";
    private static final byte[] AAD="dev.yerin.weeklymeter:v1".getBytes(StandardCharsets.UTF_8);
    private final AtomicFile file;
    Vault(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"session.bin"));}
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
    }
    Map<String,Object> read() throws Exception {
        // AtomicFile.openRead() also restores a legacy .bak after an interrupted
        // write on Android 8/9. A base-file precheck would skip that recovery.
        byte[] all;
        try{all=file.readFully();}
        catch(FileNotFoundException e){
            if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists())throw e;
            return new LinkedHashMap<>();
        }
        if(all.length<30||all.length>262144||all[0]!=1)throw new IOException("저장된 인증정보를 읽을 수 없어. 연결을 지운 뒤 다시 로그인해 줘.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(all,1,13)));cipher.updateAAD(AAD);
        byte[] clear=cipher.doFinal(Arrays.copyOfRange(all,13,all.length));
        try {return new LinkedHashMap<>(Json.object(Json.parse(new String(clear,StandardCharsets.UTF_8))));}
        finally {Arrays.fill(clear,(byte)0);}
    }
    void write(Map<String,Object> data) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(AAD);
        byte[] clear=Json.encode(data).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try {encrypted=cipher.doFinal(clear);}finally{Arrays.fill(clear,(byte)0);}
        FileOutputStream stream=null;
        try {stream=file.startWrite();stream.write(1);stream.write(cipher.getIV());stream.write(encrypted);file.finishWrite(stream);}
        catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    void clear() throws Exception {
        file.delete();KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(ALIAS);
    }
}
