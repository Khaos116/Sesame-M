"""Compile production DAV/atomic-file code and JSON mapper setup; use only a loopback server."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util"
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"


def jar(group, artifact, version):
    paths = list((CACHE / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
    assert paths, f"Missing installed dependency: {artifact} {version}"
    return paths[0]


dependencies = [jar("com.squareup.okhttp3", "okhttp", "4.12.0"), jar("com.squareup.okio", "okio-jvm", "3.6.0")]
dependencies += [jar("com.fasterxml.jackson.core", name, "2.18.2") for name in ("jackson-core", "jackson-databind", "jackson-annotations")]
kotlin = sorted((CACHE / "org.jetbrains.kotlin/kotlin-stdlib").glob("*/*/kotlin-stdlib-*.jar"), reverse=True)
dependencies.append(next(path for path in kotlin if "sources" not in path.name and "javadoc" not in path.name))

code = r'''
import io.github.aw1y2z.sesame.util.WebDavBackup;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class WebDavCheck {
    interface Attempt { void run() throws Exception; }
    static void rejected(Attempt work) throws Exception {
        try { work.run(); throw new AssertionError("unexpected success"); } catch (IOException expected) { }
    }
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static final String OLD = "{\"modelFieldsMap\":{\"BaseModel\":{\"enable\":{\"value\":false}}}}";
    static final String NEW = "{\"modelFieldsMap\":{\"BaseModel\":{\"enable\":{\"value\":true}}}}";
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File account = WebDavBackup.configFile(root, "2088");
        account.getParentFile().mkdirs(); Files.write(account.toPath(), bytes(OLD));
        rejected(() -> WebDavBackup.configFile(root, "../outside"));
        rejected(() -> WebDavBackup.configFile(root, "2088/other"));
        rejected(() -> new WebDavBackup("file:///tmp", "", "", true));
        rejected(() -> new WebDavBackup("http://localhost/dav", "", "", false));
        rejected(() -> new WebDavBackup("https://u:p@localhost/dav", "", "", false));
        rejected(() -> new WebDavBackup("https://localhost/dav?token=secret", "", "", false));
        rejected(() -> new WebDavBackup("https://localhost/dav#secret", "", "", false));
        rejected(() -> new WebDavBackup("https://localhost/dav", "bad:user", "secret", false));
        Map<String, byte[]> files = new ConcurrentHashMap<>();
        AtomicReference<String> mode = new AtomicReference<>("");
        AtomicInteger requestCount = new AtomicInteger();
        CountDownLatch pending = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService workers = Executors.newCachedThreadPool(); server.setExecutor(workers);
        server.createContext("/dav/", exchange -> {
            requestCount.incrementAndGet();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            assert "Basic dXNlcjpzZWNyZXQ=".equals(auth);
            assert exchange.getRequestURI().getQuery() == null;
            String name = exchange.getRequestURI().getPath().substring(5);
            byte[] body = new byte[0]; int status = 200;
            try {
                if (mode.get().equals("slow")) { pending.countDown(); Thread.sleep(3000); }
                if (mode.get().equals("failure")) { status = 401; body = bytes("secret should never show"); }
                else if (mode.get().equals("redirect")) { status = 302; exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/trap"); }
                else if (exchange.getRequestMethod().equals("PUT")) {
                    assert "*".equals(exchange.getRequestHeaders().getFirst("If-None-Match"));
                    files.put(name, exchange.getRequestBody().readAllBytes()); status = 201;
                } else if (exchange.getRequestMethod().equals("GET")) body = files.getOrDefault(name, bytes(NEW));
                else {
                    assert exchange.getRequestMethod().equals("PROPFIND");
                    assert "1".equals(exchange.getRequestHeaders().getFirst("Depth"));
                    status = 207;
                    StringBuilder xml = new StringBuilder("<d:multistatus xmlns:d='DAV:'>");
                    for (String n : files.keySet()) xml.append("<d:response><d:href>/dav/").append(n).append("</d:href></d:response>");
                    String good = "Sesame-M-2088-20261007111111111-config_v2.json";
                    xml.append("<d:response><d:href>http://example.com/dav/").append(good).append("</d:href></d:response>");
                    xml.append("<d:response><d:href>/outside/").append(good).append("</d:href></d:response>");
                    xml.append("<d:response><d:href>/dav/../").append(good).append("</d:href></d:response>");
                    xml.append("<d:response><d:href>/dav/").append(good).append("</d:href><d:collection/></d:response>");
                    xml.append("</d:multistatus>");
                    body = bytes(xml.toString());
                    if (mode.get().equals("xxe")) body = bytes("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///secret'>]><d:multistatus xmlns:d='DAV:'>&external;</d:multistatus>");
                    if (mode.get().equals("xml")) body = bytes("<html/>");
                    if (mode.get().equals("large")) body = new byte[1024 * 1024 + 1];
                    if (mode.get().equals("chunked-large")) { body = new byte[1024 * 1024 + 1]; exchange.sendResponseHeaders(status, 0); exchange.getResponseBody().write(body); return; }
                }
                exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                if (body.length > 0) exchange.getResponseBody().write(body);
            } catch (Exception ignored) { } finally { exchange.close(); }
        });
        server.createContext("/trap", exchange -> { throw new AssertionError("redirect followed, credentials leaked"); });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/dav/";
        try {
            WebDavBackup dav = new WebDavBackup(endpoint, "user", "secret", true);
            String name = dav.upload(root, "2088"); assert Arrays.equals(files.get(name), bytes(OLD));
            assert name.matches("Sesame-M-2088-[0-9]{17}-config_v2.json");
            files.put("Sesame-M-9999-20261007111111111-config_v2.json", bytes(NEW));
            files.put("Sesame-M-app-20261007111111111-appConfig.json", bytes("{\"newUI\":true}"));
            assert dav.list("2088").equals(List.of(name));
            assert dav.list("9999").size() == 1 && dav.list("app").size() == 1;
            rejected(() -> dav.restore(root, "2088", "../outside.json"));
            rejected(() -> dav.restore(root, "2088", "Sesame-M-9999-20261007111111111-config_v2.json"));
            assert Files.readString(account.toPath()).equals(OLD);
            files.put(name, bytes(NEW)); File prev = dav.restore(root, "2088", name);
            assert Files.readString(account.toPath()).equals(NEW) && Files.readString(prev.toPath()).equals(OLD);
            for (byte[] invalid : List.of(bytes("{"), bytes("{\"modelFieldsMap\":[]} "), bytes("{\"newUI\":true}"),
                    bytes("{\"modelFieldsMap\":{}} {}"), new byte[]{(byte)0xc3, (byte)0x28}, new byte[WebDavBackup.MAX_CONFIG_BYTES + 1])) {
                files.put(name, invalid); rejected(() -> dav.restore(root, "2088", name));
                assert Files.readString(account.toPath()).equals(NEW) && Files.readString(prev.toPath()).equals(OLD);
            }
            files.put(name, bytes(OLD));
            Files.delete(prev.toPath()); Files.createDirectory(prev.toPath());
            rejected(() -> dav.restore(root, "2088", name)); assert Files.readString(account.toPath()).equals(NEW);
            String app = "Sesame-M-app-20261007111111111-appConfig.json";
            files.put(app, bytes("{\"newUI\":true,\"darkMode\":[]}"));
            rejected(() -> dav.restore(root, "app", app));
            files.put(app, bytes("{\"newUI\":false,\"toastOffsetY\":12}")); dav.restore(root, "app", app);
            assert Files.readString(WebDavBackup.configFile(root, "app").toPath()).contains("false");
            for (String failure : List.of("failure", "redirect", "xxe", "xml", "large", "chunked-large")) {
                mode.set(failure); int count = requestCount.get(); rejected(() -> dav.list("2088")); assert requestCount.get() == count + 1;
            }
            mode.set(""); WebDavBackup cancelled = new WebDavBackup(endpoint, "user", "secret", true);
            cancelled.cancel(); int count = requestCount.get(); rejected(() -> cancelled.upload(root, "2088")); assert requestCount.get() == count;
            mode.set("slow"); WebDavBackup live = new WebDavBackup(endpoint, "user", "secret", true);
            ExecutorService caller = Executors.newSingleThreadExecutor();
            Future<?> task = caller.submit(() -> { try { rejected(() -> live.restore(root, "2088", name)); } catch (Exception e) { throw new RuntimeException(e); } });
            assert pending.await(3, TimeUnit.SECONDS); live.cancel(); task.get(3, TimeUnit.SECONDS); caller.shutdownNow();
            assert Files.readString(account.toPath()).equals(NEW);
            System.out.println("PASS WebDAV: JSON PUT/list/GET/restore, snapshot failure, scope isolation, invalid URL/path/UTF-8/JSON/XML, response bounds, redirect/auth safety and cancellation");
        } finally { server.stop(0); workers.shutdownNow(); }
    }
}
'''

ui = (ROOT / "app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixWebDavActivity.kt").read_text(encoding="utf-8")
assert "ApplicationHook" not in ui and "Xposed" not in ui
assert 'putString("password"' not in ui and "PasswordVisualTransformation()" in ui
assert "ConfirmDialog(" in ui and 'mutableStateOf<String?>(null)' in ui
with tempfile.TemporaryDirectory(prefix="sesame-webdav-") as directory:
    path = Path(directory)
    source = path / "WebDavCheck.java"
    source.write_text(code, encoding="utf-8")
    original = (SRC / "JsonUtil.java").read_text(encoding="utf-8")
    mapper = original[original.index("    private static final ObjectMapper MAPPER"):original.index("    public static String toJsonString")]
    excerpt = path / "JsonUtil.java"
    excerpt.write_text("package io.github.aw1y2z.sesame.util;\nimport com.fasterxml.jackson.databind.*;\n"
                       "import com.fasterxml.jackson.core.*;\nimport com.fasterxml.jackson.databind.type.TypeFactory;\n"
                       "import com.fasterxml.jackson.annotation.JsonInclude;\nimport java.text.SimpleDateFormat;\n"
                       "import java.util.TimeZone;\npublic class JsonUtil {\n" + mapper + "}\n", encoding="utf-8")
    cp = os.pathsep.join(map(str, dependencies))
    env = dict(os.environ, JAVA_TOOL_OPTIONS="-Xms16m -Xmx192m")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-d", directory, str(source),
                    str(excerpt), *[str(SRC / name) for name in ("WebDavBackup.java", "AtomicConfigFile.java")]], check=True, env=env)
    subprocess.run(["java", "-ea", "-cp", directory + os.pathsep + cp, "WebDavCheck", str(path / "data")], check=True, env=env)
