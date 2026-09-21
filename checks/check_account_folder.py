#!/usr/bin/env python3
"""账号目录/导出文件名用账号名：先取配置页账号列表括号前面的名字（如 C176），没有再取括号里面的账号，最后才用 uid。

直接编译生产代码 util/AccountFolderName.java（纯逻辑，读写通过接口注入），用内存实现回放：
- 名字可用就用名字；名字不可用（空/纯符号/叫 default）改用账号；两者都不可用或读取失败才用 uid；uid 不合法用 default；
- 名字做文件名安全处理（路径分隔符、点、空白、超长）；中文名保留；
- 同名的两个账号后来者加 uid 后 4 位区分；
- 旧的 uid 目录只在首次解析时迁移一次；每个 uid 一个进程里只解析一次（日志写入器按目录创建，不能中途换）；
- 从文件读回来的目录名（current_log_user.txt）必须是安全的目录名。
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/AccountFolderName.java"

HARNESS = r'''
package io.github.aw1y2z.sesame.util;

import java.util.*;

public class AccountFolderNameCheck {
    static class FakeSource implements AccountFolderName.Source {
        final Map<String, String> names = new HashMap<>();
        final Map<String, String> accounts = new HashMap<>();
        int calls;
        boolean throwing;
        /** 与生产一致：先括号前面的名字，再括号里面的账号。 */
        public String[] candidates(String uid) {
            calls++;
            if (throwing) throw new IllegalStateException("self.json unreadable");
            if (!names.containsKey(uid) && !accounts.containsKey(uid)) return null;
            return new String[]{names.get(uid), accounts.get(uid)};
        }
    }

    static class FakeStore implements AccountFolderName.Store {
        final Map<String, String> owners = new HashMap<>();
        final List<String> migrations = new ArrayList<>();
        public String owner(String folder) { return owners.get(folder); }
        public void migrate(String uid, String folder) { migrations.add(uid + "->" + folder); }
        public void claim(String folder, String uid) { owners.putIfAbsent(folder, uid); }
    }

    static FakeSource source;
    static FakeStore store;

    static void fresh() {
        source = new FakeSource();
        store = new FakeStore();
        AccountFolderName.install(source, store);
    }

    static void eq(String expected, String actual, String what) {
        assert expected.equals(actual) : what + ": expected [" + expected + "] got [" + actual + "]";
    }

    public static void main(String[] args) {
        fresh();
        String uid = "2088702045701743";

        // 没有名字 → uid；uid 不合法 → default
        eq(uid, AccountFolderName.resolve(uid), "no name");
        eq("default", AccountFolderName.resolve(null), "null uid");
        eq("default", AccountFolderName.resolve("../escape"), "path uid");
        eq("default", AccountFolderName.resolve(""), "empty uid");

        // 有名字 → 名字，并迁移旧 uid 目录、记下归属
        fresh();
        source.names.put(uid, "C176");
        eq("C176", AccountFolderName.resolve(uid), "label");
        eq("C176", store.owners.containsKey("C176") ? "C176" : "", "claimed");
        eq(uid, store.owners.get("C176"), "owner");
        eq("[" + uid + "->C176]", store.migrations.toString(), "migrated once");
        eq("C176", AccountFolderName.resolve(uid), "cached");
        assert store.migrations.size() == 1 && source.calls == 1 : "resolved once per process";
        eq("C176", AccountFolderName.displayLabel(uid), "displayLabel");

        // 名字不可用（空/纯符号/叫 default）且没有账号 → 用 uid
        for (String bad : new String[]{"", "   ", "!!!", "///", "default"}) {
            fresh();
            source.names.put(uid, bad);
            eq(uid, AccountFolderName.resolve(uid), "bad label [" + bad + "]");
            eq(uid, AccountFolderName.displayLabel(uid), "bad label display [" + bad + "]");
        }

        // 优先级：括号前面的名字 → 括号里面的账号 → uid
        fresh();
        source.names.put(uid, "C176");
        source.accounts.put(uid, "user@example.com");
        eq("C176", AccountFolderName.resolve(uid), "name wins over account");
        for (String badName : new String[]{"", "   ", "!!!", "default"}) {
            fresh();
            source.names.put(uid, badName);
            source.accounts.put(uid, "user@example.com");
            eq("user_example_com", AccountFolderName.resolve(uid), "account used when name unusable [" + badName + "]");
            eq("user_example_com", AccountFolderName.displayLabel(uid), "account display [" + badName + "]");
        }
        fresh();
        source.accounts.put(uid, "138****1234"); // 名字缺失只有账号（手机号带掩码）
        eq("138_1234", AccountFolderName.resolve(uid), "masked phone account");
        fresh();
        source.names.put(uid, "!!!");
        source.accounts.put(uid, "///"); // 两个都不可用 → uid
        eq(uid, AccountFolderName.resolve(uid), "both unusable falls back to uid");
        eq(uid, AccountFolderName.displayLabel(uid), "both unusable display");
        fresh();
        source.throwing = true;
        eq(uid, AccountFolderName.resolve(uid), "unreadable");
        eq(uid, AccountFolderName.displayLabel(uid), "unreadable display");

        // 安全化：路径分隔符/点/空白，中文保留，超长截断
        eq("a_b", AccountFolderName.sanitize("a/b"), "slash");
        eq("a_b", AccountFolderName.sanitize("a\\b"), "backslash");
        eq("a_b", AccountFolderName.sanitize("a..b"), "dots");
        eq("_", AccountFolderName.sanitize("../").isEmpty() ? "_" : "x", "traversal collapses to empty");
        eq("有飞", AccountFolderName.sanitize("有飞"), "cjk");
        eq("C176", AccountFolderName.sanitize(" C176 "), "trim");
        assert AccountFolderName.sanitize("x".repeat(100)).length() == AccountFolderName.MAX_LABEL : "max length";
        fresh();
        source.names.put(uid, "../../etc");
        String traversal = AccountFolderName.resolve(uid);
        assert !traversal.contains("/") && !traversal.contains("..") : "traversal must not survive: " + traversal;

        // 同名账号：后来者加 uid 后 4 位
        fresh();
        String other = "2088942846628038";
        source.names.put(uid, "C176");
        source.names.put(other, "C176");
        eq("C176", AccountFolderName.resolve(uid), "first owner");
        eq("C176-8038", AccountFolderName.resolve(other), "same name disambiguated");
        eq("C176", AccountFolderName.resolve(uid), "first still stable");
        // 重启后同一个账号还是拿到自己的目录（标记里记着归属）
        AccountFolderName.install(source, store);
        eq("C176-8038", AccountFolderName.resolve(other), "other stable after restart");
        eq("C176", AccountFolderName.resolve(uid), "first stable after restart");

        // 目录名安全检查（读 current_log_user.txt 用）
        for (String ok : new String[]{"C176", "有飞", "2088702045701743", "C176-8038", "a_b"}) {
            assert AccountFolderName.isSafeFolder(ok) : "safe expected: " + ok;
        }
        for (String bad : new String[]{"", ".", "..", "../x", "a/b", "a\\b", "a b", "a.b", "x".repeat(65), null}) {
            assert !AccountFolderName.isSafeFolder(bad) : "unsafe expected: " + bad;
        }
        System.out.println("PASS: account folder names use the account-list name, fall back to uid, stay path-safe");
    }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sesame-account-folder-") as work:
        work = Path(work)
        probe = work / "AccountFolderNameCheck.java"
        probe.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), str(SOURCE), str(probe)], check=True)
        subprocess.run(["java", "-ea", "-cp", str(work),
                        "io.github.aw1y2z.sesame.util.AccountFolderNameCheck"], check=True, timeout=60)


if __name__ == "__main__":
    sys.exit(main())
