/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.monitor.runtime.io;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.core.util.validation.CheckUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * 备份包加密与摘要支持（FR-IO-07）
 *
 * <p>加密包为 {@code *.zip.enc}：文件头 JDRKBK1 + 16 字节盐 + 12 字节 IV + AES-256-GCM 密文，
 * 密钥由口令经 PBKDF2-HMAC-SHA256（12 万次）派生。与 {@link BackupService} 的打包/恢复流程分离，
 * 便于单独测试与复用（NFR-MAINT-03 文件规模）。</p>
 *
 * @author jdrk
 * @since 2026/9/25
 */
@Slf4j
@Component
public class BackupPackageSupport {

    /** 加密包文件头 */
    private static final byte[] MAGIC = "JDRKBK1".getBytes(StandardCharsets.US_ASCII);

    /** PBKDF2 迭代次数 */
    private static final int ITERATIONS = 120_000;

    /** 盐长度 */
    private static final int SALT_LENGTH = 16;

    /** GCM IV 长度 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度（位） */
    private static final int TAG_BITS = 128;

    /** 加密用随机源（每个随机值都用同一实例，避免新建后只用一次的弱随机告警） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 是否为加密包
     *
     * @param file 文件
     * @return 是否加密
     */
    public boolean isEncrypted(Path file) {
        return file.getFileName().toString().endsWith(".enc");
    }

    /**
     * 写出加密头（文件头 + 盐 + IV）
     *
     * @param out 输出流
     * @return 本次使用的盐与 IV
     * @throws IOException 写出失败
     */
    public Secrets writeHeader(OutputStream out) throws IOException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);
        out.write(MAGIC);
        out.write(salt);
        out.write(iv);
        out.flush();
        return new Secrets(salt, iv);
    }

    /**
     * 按口令与盐/IV 初始化 AES-GCM 加解密器
     *
     * @param password 口令
     * @param mode     模式（{@link Cipher#ENCRYPT_MODE} / {@link Cipher#DECRYPT_MODE}）
     * @param secrets  盐与 IV
     * @return 已初始化的 cipher
     */
    public Cipher cipher(String password, int mode, Secrets secrets) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), secrets.salt(), ITERATIONS, 256);
            SecretKeySpec key =
                new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, secrets.iv()));
            return cipher;
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密算法失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解密备份包到目标文件（口令错误或文件被篡改会在 GCM 认证标签处失败）
     *
     * @param source   加密包
     * @param password 口令
     * @param target   解密输出
     */
    public void decrypt(Path source, String password, Path target) {
        CheckUtils.throwIf(password == null || password.isBlank(), "该备份包已加密，必须提供口令");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            CheckUtils.throwIf(magic.length != MAGIC.length || !MessageDigest.isEqual(magic, MAGIC),
                "备份包格式不正确");
            byte[] salt = in.readNBytes(SALT_LENGTH);
            byte[] iv = in.readNBytes(IV_LENGTH);
            CheckUtils.throwIf(salt.length != SALT_LENGTH || iv.length != IV_LENGTH, "备份包头部不完整");
            Cipher cipher = this.cipher(password, Cipher.DECRYPT_MODE, new Secrets(salt, iv));
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                byte[] plain = cipher.update(buffer, 0, read);
                if (plain != null && plain.length > 0) {
                    out.write(plain);
                }
            }
            // doFinal 会校验 GCM 认证标签，口令错误或包被篡改在这里抛出
            byte[] tail = cipher.doFinal();
            if (tail != null && tail.length > 0) {
                out.write(tail);
            }
        } catch (IOException e) {
            throw new IllegalStateException("解密备份包失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("解密备份包失败（口令错误或文件损坏）：" + e.getMessage(), e);
        }
    }

    /**
     * 流对拷（并在结束时关闭输入流）
     *
     * @param in  输入流
     * @param out 输出流
     * @throws IOException 读写失败
     */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        try (InputStream source = in) {
            source.transferTo(out);
        }
    }

    /**
     * 新建 SHA-256 摘要器
     *
     * @return 摘要器
     */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 计算流的 SHA-256
     *
     * @param in 输入流
     * @return 摘要
     * @throws IOException 读取失败
     */
    public static byte[] sha256(InputStream in) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    /**
     * 静默删除（清理临时文件失败不影响主流程）
     *
     * @param path 文件，可为空
     */
    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除临时文件失败：{}", e.getMessage());
        }
    }

    /**
     * 加密头参数
     *
     * @param salt 盐
     * @param iv   IV
     */
    public record Secrets(byte[] salt, byte[] iv) {
    }
}
