package io.github.aw1y2z.sesame.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Manual transfers of M's exported JSON; remote paths never become local paths. */
public final class WebDavBackup {
    public static final int MAX_CONFIG_BYTES = 2 * 1024 * 1024;
    private final HttpUrl directory;
    private final String authorization;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build();
    private Call active;
    private boolean cancelled;

    public WebDavBackup(String url, String username, String password, boolean allowHttp) throws IOException {
        HttpUrl parsed = HttpUrl.parse(url == null ? "" : url.trim());
        if (parsed == null || (!parsed.isHttps() && !allowHttp) || !parsed.username().isEmpty()
                || !parsed.password().isEmpty() || parsed.query() != null || parsed.fragment() != null) {
            throw new IOException("请输入 HTTPS WebDAV 目录；HTTP 需单独允许，地址不能带账号、查询或片段");
        }
        directory = parsed.newBuilder().encodedPath(parsed.encodedPath().replaceAll("/+$", "") + "/").build();
        String user = username == null ? "" : username;
        String pass = password == null ? "" : password;
        if (user.contains(":") || user.contains("\r") || user.contains("\n")) throw new IOException("用户名无效");
        authorization = user.isEmpty() && pass.isEmpty() ? null : Credentials.basic(user, pass, StandardCharsets.UTF_8);
    }

    public synchronized void cancel() {
        cancelled = true;
        if (active != null) active.cancel();
    }

    private synchronized void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("操作已取消");
    }

    public static boolean isAccountId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,80}") && !id.equals("default") && !id.equals("app");
    }

    private static void requireScope(String scope) throws IOException {
        if (!"app".equals(scope) && !"default".equals(scope) && !isAccountId(scope)) throw new IOException("账号范围无效");
    }

    public static File configFile(File root, String scope) throws IOException {
        requireScope(scope);
        root = root.getCanonicalFile();
        File target = "app".equals(scope) ? new File(root, "appConfig.json")
                : "default".equals(scope) ? new File(root, "config_v2.json")
                : new File(new File(new File(root, "config"), scope), "config_v2.json");
        if (!target.getCanonicalFile().equals(target.getAbsoluteFile())) throw new IOException("配置路径无效");
        return target;
    }

    private static String suffix(String scope) {
        return "app".equals(scope) ? "appConfig.json" : "config_v2.json";
    }

    private static boolean matches(String name, String scope) {
        return name.matches("Sesame-M-" + java.util.regex.Pattern.quote(scope) + "-[0-9]{17}-" + java.util.regex.Pattern.quote(suffix(scope)));
    }

    public String upload(File root, String scope) throws IOException {
        checkCancelled();
        byte[] bytes = readFile(configFile(root, scope));
        validate(bytes, scope);
        SimpleDateFormat timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.ROOT);
        timestamp.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        String name = "Sesame-M-" + scope + "-" + timestamp.format(new Date()) + "-" + suffix(scope);
        exchange("PUT", directory.newBuilder().addPathSegment(name).build(), bytes, MAX_CONFIG_BYTES);
        return name;
    }

    public List<String> list(String scope) throws IOException {
        requireScope(scope);
        byte[] xml = exchange("PROPFIND", directory, ("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\">"
                + "<d:prop><d:resourcetype/></d:prop></d:propfind>").getBytes(StandardCharsets.UTF_8), 1024 * 1024);
        List<String> names = new ArrayList<>();
        try {
            String text = decode(xml);
            if (text.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || text.toUpperCase(Locale.ROOT).contains("<!ENTITY")) {
                throw new IOException("WebDAV 列表包含不安全 XML");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder parser = factory.newDocumentBuilder();
            parser.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
            org.w3c.dom.Document doc = parser.parse(new InputSource(new java.io.StringReader(text)));
            if (!"multistatus".equals(doc.getDocumentElement().getLocalName())
                    || !"DAV:".equals(doc.getDocumentElement().getNamespaceURI())) throw new IOException("WebDAV 列表格式无效");
            NodeList responses = doc.getElementsByTagNameNS("DAV:", "response");
            if (responses.getLength() > 1000) throw new IOException("WebDAV 文件过多");
            for (int i = 0; i < responses.getLength(); i++) {
                Element response = (Element) responses.item(i);
                NodeList hrefs = response.getElementsByTagNameNS("DAV:", "href");
                if (hrefs.getLength() != 1 || response.getElementsByTagNameNS("DAV:", "collection").getLength() != 0) continue;
                HttpUrl href = directory.resolve(hrefs.item(0).getTextContent().trim());
                if (href == null || !href.scheme().equals(directory.scheme()) || !href.host().equals(directory.host())
                        || href.port() != directory.port() || !href.username().isEmpty() || !href.password().isEmpty()
                        || href.query() != null || href.fragment() != null) continue;
                List<String> parts = href.pathSegments();
                String name = parts.get(parts.size() - 1);
                if (matches(name, scope) && href.equals(directory.newBuilder().addPathSegment(name).build()) && !names.contains(name)) names.add(name);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("WebDAV 列表格式无效");
        }
        checkCancelled();
        Collections.sort(names, Collections.reverseOrder());
        return names;
    }

    public File restore(File root, String scope, String name) throws IOException {
        requireScope(scope);
        if (name == null || !matches(name, scope)) throw new IOException("备份文件与所选范围不符");
        byte[] bytes = exchange("GET", directory.newBuilder().addPathSegment(name).build(), null, MAX_CONFIG_BYTES);
        String text = validate(bytes, scope);
        File target = configFile(root, scope);
        return commit(target, text);
    }

    private synchronized File commit(File target, String text) throws IOException {
        checkCancelled();
        File previous = new File(target.getParentFile(), target.getName().replace(".json", ".prev.json"));
        boolean existed = target.exists();
        // Unlike the legacy best-effort backup, a failed snapshot must stop a remote restore.
        if (existed) AtomicConfigFile.write(decode(readFile(target)), previous);
        AtomicConfigFile.write(text, target);
        return existed ? previous : null;
    }

    private static byte[] readFile(File file) throws IOException {
        if (!file.isFile() || file.length() > MAX_CONFIG_BYTES) throw new IOException("配置不存在或超过 2 MiB");
        try (InputStream input = Files.newInputStream(file.toPath())) { return readBounded(input, MAX_CONFIG_BYTES); }
    }

    private static String validate(byte[] bytes, String scope) throws IOException {
        String text = decode(bytes);
        JsonNode node;
        try {
            node = JsonUtil.copyMapper().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
        } catch (Exception e) { throw new IOException("备份 JSON 无效"); }
        if (node == null || !node.isObject() || ("app".equals(scope)
                ? !node.path("newUI").isBoolean() || node.has("modelFieldsMap")
                : !node.path("modelFieldsMap").isObject())) throw new IOException("备份不是所选范围的 M 配置");
        if ("app".equals(scope)) {
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (!value.isNull() && ("toastOffsetY".equals(field.getKey())
                        ? !value.isIntegralNumber() || !value.canConvertToInt() : !value.isBoolean())) {
                    throw new IOException("全局配置字段类型无效");
                }
            }
        }
        return text;
    }

    private static String decode(byte[] bytes) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IOException("备份必须使用 UTF-8"); }
    }

    private byte[] exchange(String method, HttpUrl url, byte[] content, int limit) throws IOException {
        checkCancelled();
        Request.Builder builder = new Request.Builder().url(url);
        if (authorization != null) builder.header("Authorization", authorization);
        if ("PROPFIND".equals(method)) builder.header("Depth", "1");
        if ("PUT".equals(method)) builder.header("If-None-Match", "*");
        builder.method(method, content == null ? null : RequestBody.create(content,
                MediaType.get("PROPFIND".equals(method) ? "application/xml; charset=utf-8" : "application/json; charset=utf-8")));
        Call call = http.newCall(builder.build());
        synchronized (this) { checkCancelled(); active = call; }
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("WebDAV 请求失败 (HTTP " + response.code() + ")");
            if (response.body() == null) return new byte[0];
            if (response.body().contentLength() > limit) throw new IOException("WebDAV 响应超过大小限制");
            byte[] bytes = readBounded(response.body().byteStream(), limit);
            checkCancelled();
            return bytes;
        } catch (IOException e) {
            checkCancelled();
            // Network exceptions can contain the endpoint; keep credentials/addresses out of UI and logs.
            if (e.getMessage() != null && e.getMessage().startsWith("WebDAV")) throw e;
            throw new IOException("WebDAV 连接失败，请检查网络、地址和认证");
        } finally {
            synchronized (this) { active = null; }
        }
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > limit) throw new IOException("WebDAV 响应超过大小限制");
            output.write(buffer, 0, size);
        }
        return output.toByteArray();
    }
}
