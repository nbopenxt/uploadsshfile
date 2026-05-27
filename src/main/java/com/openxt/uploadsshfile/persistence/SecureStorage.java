package com.openxt.uploadsshfile.persistence;

import com.openxt.uploadsshfile.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全存储
 * 使用加密文件存储密码
 */
public class SecureStorage {
    private static SecureStorage instance;

    private SecureStorage() {
    }

    public static SecureStorage getInstance() {
        if (instance == null) {
            synchronized (SecureStorage.class) {
                if (instance == null) {
                    instance = new SecureStorage();
                }
            }
        }
        return instance;
    }

    /**
     * 存储密码到加密文件
     */
    public void store(String key, String password) {
        Logger.debug("SecureStorage", "store() called for key=" + key);
        fallbackStore(key, password);
        Logger.debug("SecureStorage", "store() completed");
    }

    /**
     * 从加密文件获取密码
     */
    public String retrieve(String key) {
        Logger.debug("SecureStorage", "retrieve() called for key=" + key);
        String result = fallbackRetrieve(key);
        Logger.debug("SecureStorage", "retrieve() completed, found=" + (result != null));
        return result;
    }

    /**
     * 删除密码
     */
    public void delete(String key) {
        Logger.debug("SecureStorage", "delete() called for key=" + key);
        fallbackDelete(key);
        Logger.debug("SecureStorage", "delete() completed");
    }

    // ==================== 加密文件存储 ====================

    private static final File FALLBACK_FILE = new File(
            System.getProperty("user.home") + "/.uploadsshfile/secure.dat");

    private void fallbackStore(String key, String password) {
        FALLBACK_FILE.getParentFile().mkdirs();
        Map<String, String> map = fallbackLoad();
        map.put(key, encrypt(password));
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(FALLBACK_FILE), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            // 忽略
        }
    }

    private String fallbackRetrieve(String key) {
        Map<String, String> map = fallbackLoad();
        String encrypted = map.get(key);
        if (encrypted != null) {
            return decrypt(encrypted);
        }
        return null;
    }

    private void fallbackDelete(String key) {
        Map<String, String> map = fallbackLoad();
        if (map.remove(key) != null) {
            // 重新写入文件
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(FALLBACK_FILE), StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            } catch (IOException e) {
                // 忽略
            }
        }
    }

    private Map<String, String> fallbackLoad() {
        Map<String, String> map = new HashMap<>();
        if (!FALLBACK_FILE.exists()) {
            return map;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(FALLBACK_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    map.put(line.substring(0, idx), line.substring(idx + 1));
                }
            }
        } catch (IOException e) {
            // 忽略
        }
        return map;
    }

    private String encrypt(String password) {
        try {
            byte[] data = password.getBytes(StandardCharsets.UTF_8);
            byte[] key = "uploadsshfile_key".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                encrypted[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return password;
        }
    }

    private String decrypt(String encrypted) {
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            byte[] key = "uploadsshfile_key".getBytes(StandardCharsets.UTF_8);
            byte[] decrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                decrypted[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
