package com.openxt.uploadsshfile.persistence;

import com.openxt.uploadsshfile.util.Logger;
import com.openxt.uploadsshfile.util.PluginPathManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    /**
     * 加载所有密码（加密形式），用于导入回滚时备份。
     * @return key → 加密后的 Base64 字符串
     */
    public Map<String, String> loadAll() {
        return fallbackLoad();
    }

    /**
     * 全量恢复密码数据，用于导入回滚。
     * @param data key → 加密后的 Base64 字符串
     */
    public void restore(Map<String, String> data) {
        File file = getStorageFile();
        file.getParentFile().mkdirs();
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            Logger.debug("SecureStorage", "restore() failed: " + e.getMessage());
        }
    }

    // ==================== 加密文件存储 ====================

    /**
     * 获取密码加密文件路径（新位置）。
     * 存储在 {IDEA_CONFIG}/uploadsshfile/secure.dat，与 plugin-config.json 同目录。
     */
    private File getStorageFile() {
        PluginPathManager pathManager = PluginPathManager.getInstance();
        Path configPath = pathManager.ensureDirectory(pathManager.getConfigPath());
        return new File(configPath.toFile(), "secure.dat");
    }

    /**
     * 获取旧版密码文件路径（兼容性读取）。
     * 旧版位置：~/.uploadsshfile/secure.dat
     */
    private File getOldStorageFile() {
        return new File(System.getProperty("user.home") + "/.uploadsshfile/secure.dat");
    }

    private void fallbackStore(String key, String password) {
        File file = getStorageFile();
        file.getParentFile().mkdirs();
        Map<String, String> map = fallbackLoad();
        map.put(key, encrypt(password));
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            // 忽略
        }
        clearOldFileIfExists();
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
            File file = getStorageFile();
            // 重新写入文件
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            } catch (IOException e) {
                // 忽略
            }
            clearOldFileIfExists();
        }
    }

    /**
     * 读取密码数据，兼容新旧两个位置。
     * 先读旧路径（作为基础），再读新路径（覆盖同名 key），实现无缝迁移。
     */
    private Map<String, String> fallbackLoad() {
        Map<String, String> map = new HashMap<>();
        File oldFile = getOldStorageFile();
        File newFile = getStorageFile();

        // 1. 先读旧路径（兼容旧版本数据）
        if (oldFile.exists()) {
            loadFromFile(oldFile, map);
        }

        // 2. 再读新路径（同名 key 以新路径值为准）
        if (newFile.exists()) {
            loadFromFile(newFile, map);
        }

        return map;
    }

    /**
     * 从文件逐行读取 key=value 格式的密码数据。
     */
    private void loadFromFile(File file, Map<String, String> map) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
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
    }

    /**
     * 清理旧版密码文件。写入新位置后，自动删除旧文件，确保数据只存一份。
     */
    private void clearOldFileIfExists() {
        File oldFile = getOldStorageFile();
        if (oldFile.exists()) {
            if (oldFile.delete()) {
                Logger.debug("SecureStorage", "Removed old password file: " + oldFile.getAbsolutePath());
            }
        }
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
