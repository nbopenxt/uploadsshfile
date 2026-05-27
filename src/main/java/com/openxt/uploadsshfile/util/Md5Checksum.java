package com.openxt.uploadsshfile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 校验工具类
 *
 * <p>提供本地文件 MD5 计算功能，用于上传后与远程文件校验对比。
 */
public class Md5Checksum {

    private static final int BUFFER_SIZE = 8192;

    /**
     * 计算文件的 MD5 值
     *
     * @param file 文件对象
     * @return MD5 十六进制字符串（小写）
     * @throws IOException 如果读取文件失败
     */
    public static String calculate(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] digest = md.digest();
            return bytesToHex(digest);
            
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法总是存在
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * 验证两个 MD5 值是否相等
     *
     * @param md5a 第一个 MD5 值
     * @param md5b 第二个 MD5 值
     * @return true 如果相等（忽略大小写）
     */
    public static boolean verify(String md5a, String md5b) {
        if (md5a == null || md5b == null) {
            return false;
        }
        return md5a.toLowerCase().equals(md5b.toLowerCase());
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
