package io.github.aw1y2z.sesame.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 配置文件专用的原子替换写入：先写临时文件、校验回读一致后再 rename 到目标路径，
 * 不会出现"写到一半被杀进程导致配置文件截断"的情况。
 * 移植自 GR 分支的账号轮询功能，参见 doc/MyFix.md。
 */
public final class AtomicConfigFile {
    interface Operations {
        OutputStream open(File file) throws IOException;
        void sync(OutputStream stream) throws IOException;
        byte[] read(File file) throws IOException;
        boolean replace(File temporary, File target) throws IOException;
    }

    static final Operations FILES = new Operations() {
        public OutputStream open(File file) throws IOException { return new FileOutputStream(file); }
        public void sync(OutputStream stream) throws IOException { ((FileOutputStream) stream).getFD().sync(); }
        public byte[] read(File file) throws IOException {
            try (FileInputStream in = new FileInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
                return out.toByteArray();
            }
        }
        public boolean replace(File temporary, File target) { return temporary.renameTo(target); }
    };

    private AtomicConfigFile() { }

    public static void write(String content, File target) throws IOException {
        write(content, target, FILES);
    }

    static synchronized void write(String content, File target, Operations operations) throws IOException {
        if (content == null || content.trim().isEmpty()) throw new IOException("empty configuration");
        File absolute = target.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if ((!parent.isDirectory() && !parent.mkdirs()) || absolute.isDirectory()) {
            throw new IOException("configuration destination is not a file");
        }
        byte[] expected = content.getBytes(StandardCharsets.UTF_8);
        File temporary = File.createTempFile("config-", ".pending", parent);
        try {
            try (OutputStream stream = operations.open(temporary)) {
                stream.write(expected);
                stream.flush();
                operations.sync(stream);
            }
            if (!Arrays.equals(expected, operations.read(temporary))) {
                throw new IOException("configuration read-back mismatch");
            }
            if (!operations.replace(temporary, absolute)) throw new IOException("configuration rename failed");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }
}
