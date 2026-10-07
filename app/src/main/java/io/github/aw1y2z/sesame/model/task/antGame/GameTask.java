package io.github.aw1y2z.sesame.model.task.antGame;

import io.github.aw1y2z.sesame.util.MyUtils;


import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 游戏任务上报工具类
 * 对应原Kotlin的GameTask枚举类
 */
public enum GameTask {

    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),
    Forest_slxcc("森林小车车", "2060170000363691", "zfb_slxcc", "slxcc_game_kaiche_every_10", "lianyun_senlin_leyuan", "1.0.1", 3),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    private final String title;
    private final String appId;
    private final String gid;
    private final String action;
    private final String channel;
    private final String version;
    private final int requestsPerEgg; // 完成1个🥚要多少次 为了防止网络崩溃 多加1次
    private String cachedToken; // 缓存登录Token

    /**
     * 根据小程序 appId 匹配游戏任务（金豆乐园游戏权益上报使用）
     */
    public static GameTask matchAppId(String appId) {
        if (appId == null || appId.isEmpty()) {
            return null;
        }
        for (GameTask task : values()) {
            if (appId.equals(task.appId)) {
                return task;
            }
        }
        return null;
    }

    /**
     * 根据任务类型匹配游戏（能量雨结束列表下发的游戏任务使用）。
     * <p>taskType 形如 {@code GAME_DONE_SLJYD}：前缀固定，后缀是游戏代号。而这个代号恰好是枚举里
     * gid/action 的缩写（SLJYD → zfb_sljydx / sljyd_game_xiaochu_every_10），所以按代号在 gid、
     * action 里做包含匹配即可，不必为每个游戏单独写一个 if。
     *
     * @return 枚举里没有对应常量的游戏返回 null
     */
    public static GameTask matchTaskType(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return null;
        }
        String key = taskType.toUpperCase(Locale.ROOT);
        if (key.startsWith("GAME_DONE_")) {
            key = key.substring("GAME_DONE_".length());
        }
        key = key.toLowerCase(Locale.ROOT);
        if (key.length() < 4) {
            return null;
        }
        // 有些 taskType 会带后缀（如 SLJYD_XS_3），取第一段代号再匹配一次
        String head = key.contains("_") ? key.substring(0, key.indexOf('_')) : key;
        for (GameTask task : values()) {
            if (task.gid.toLowerCase(Locale.ROOT).contains(key)
                    || task.action.toLowerCase(Locale.ROOT).contains(key)) {
                return task;
            }
            if (head.length() >= 4
                    && (task.gid.toLowerCase(Locale.ROOT).contains(head)
                    || task.action.toLowerCase(Locale.ROOT).contains(head))) {
                return task;
            }
        }
        return null;
    }

    public String getAppId() {
        return appId;
    }

    public String getTitle() {
        return title;
    }

    /**
     * 枚举构造方法
     */
    GameTask(String title, String appId, String gid, String action, String channel, String version, int requestsPerEgg) {
        this.title = title;
        this.appId = appId;
        this.gid = gid;
        this.action = action;
        this.channel = channel;
        this.version = version;
        this.requestsPerEgg = requestsPerEgg;
    }

    /**
     * 第一步：登录获取 Token 并缓存
     */
    private String login() {
        return login(() -> { });
    }

    private String login(Runnable checkpoint) {
        try {
            checkpoint.run();
            String authCode = AuthCodeHelper.getAuthCode(appId);
            checkpoint.run();
            if (authCode == null || authCode.isEmpty()) return null;
            // 小程序标记（紧邻的 alipayMiniMark 请求头）在当前支付宝版本上必然为空：
            // 承载它的宿主类 H5HttpUtils 已不存在（AlipayMiniMarkHelper 探测两个候选类名都找不到，
            // 并会在日志里说明一次）。这里直接用空串，省掉逐游戏的反射调用；
            // 将来某版支付宝恢复该能力时，把空串换回 AlipayMiniMarkHelper.getAlipayMiniMark(appId, version) 即可。
            String mark = "";
            String reqId = System.currentTimeMillis() + "_" + new Random().nextInt(350) + 1;

            JSONObject bodyJson = MyUtils.newJSONObject();
            bodyJson.put("v", version);
            // 授权助手复用宿主代理服务；尚未就绪时可能返回 null，保留游戏服原有登录行为。
            bodyJson.put("code", authCode);
            bodyJson.put("pf", "zfb");
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("version", version);
            String body = bodyJson.toString();

            //Log.other("login 请求体 -> " + body);

            // 建立HTTP连接
            URL url = new URL("https://gamesapi2.aslk2018.com/v2/game/login");
            checkpoint.run();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            // 防止外部游戏服连接挂起导致当前线程无限阻塞（主任务线程会因此冻结，只能重启恢复）
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("User-Agent", getDynamicUA());
            conn.setRequestProperty("x-release-type", "ONLINE");

            // 写入请求体
            checkpoint.run();
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                checkpoint.run();
                writer.write(body);
            }

            // 处理响应（包含错误流）
            int respCode = conn.getResponseCode();
            if (respCode < 200 || respCode >= 300) return null;
            StringBuilder responseText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ))) {
                char[] buffer = new char[2048];
                for (;;) {
                    checkpoint.run();
                    int count = reader.read(buffer);
                    checkpoint.run();
                    if (count < 0) break;
                    if (responseText.length() + count > 65_536) throw new IOException("SDK response exceeds limit");
                    responseText.append(buffer, 0, count);
                }
            }
            checkpoint.run();

            //Log.other("login 响应 -> HTTP " + respCode + " " + responseText);

            // 解析响应JSON
            JSONObject resJson = MyUtils.newJSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                JSONObject data = resJson.optJSONObject("data");
                if (data != null) {
                    Object value = data.opt("token");
                    if (!(value instanceof String) || ((String) value).trim().isEmpty()) return null;
                    Log.other("登录成功✅Token已获取");
                    return (String) value;
                }
            } else {
                Log.error("游戏SDK登录未接受#HTTP[" + respCode + "]#code[" + resJson.optInt("code", 0) + "]");
            }
            } finally { conn.disconnect(); }
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Exception e) {
            Log.error("游戏SDK登录异常#" + e.getClass().getSimpleName());
        }
        return null;
    }

    /**
     * 外部调用：执行上报任务
     * @param eggCount 目标蛋数量
     */
    public void report(String gameType,int eggCount) {
        int totalNeeded = eggCount * (this.requestsPerEgg + 1); // 多1次确保网络请求不会错误
        final String reportUid = UserIdMap.getCurrentUid();
        // 提交前计数，覆盖等待线程启动的窗口。
        TaskLifecycle.Work work = TaskLifecycle.enter();
        if (work == null) return;
        try {
            new Thread(() -> {
                try (TaskLifecycle.Work admitted = work) {
                    if (!java.util.Objects.equals(reportUid, UserIdMap.getCurrentUid())) {
                        Log.record("任务流程🛑账号已切换，停止上报");
                        return;
                    }
                    this.cachedToken = login();
                    if (this.cachedToken == null || this.cachedToken.isEmpty()) {
                         Log.error("无法获取⚠️有效的Token，放弃上报任务");
                        return;
                    }

                    Log.record("开始执行🚀"+gameType+"游戏任务:目标" + eggCount + "个蛋，需请求" + totalNeeded + "次");
                    for (int i = 1; i <= totalNeeded; i++) {
                        if (!java.util.Objects.equals(reportUid, UserIdMap.getCurrentUid())) {
                            Log.record("任务流程🛑账号已切换，停止上报");
                            break;
                        }
                        if (!executeSingleReport(gameType,i, totalNeeded)) {
                            // 具体的错误原因已在 executeSingleReport 中详细输出
                            break;
                        }
                        if (i < totalNeeded) {
                            try {
                                Thread.sleep(new Random().nextInt(2001) + 1000); // 1000-3000ms随机休眠
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    Log.record("任务流程🏁运行结束");
                }
            }).start();
        } catch (RuntimeException | Error failure) {
            work.close();
            throw failure;
        }
    }

    /**
     * 同步执行上报任务，返回成功上报次数。
     * 用于需要等待结果并回查服务端状态的场景（如金豆乐园游戏权益）。
     *
     * @param gameType 日志展示用的场景名
     * @param eggCount 目标蛋数量
     * @return 成功上报的次数，失败返回已成功的次数
     */
    public int reportSync(String gameType, int eggCount) {
        return reportSync(gameType, eggCount, null);
    }

    /**
     * 同步执行上报任务，可覆盖上报渠道。
     * <p>金豆乐园场景必须传 {@code "goldenbean"}，
     * 否则游戏服接受上报但支付宝侧权益不推进。
     *
     * @param channelOverride 非空时覆盖 action_finish_channel；为空用枚举默认渠道
     */
    public int reportSync(String gameType, int eggCount, String channelOverride) {
        if (eggCount <= 0 || eggCount > 1000) {
            return 0;
        }
        final String reportUid = UserIdMap.getCurrentUid();
        final long generation = TaskLifecycle.generation();
        if (reportUid == null || reportUid.isEmpty()) return 0;
        Runnable checkpoint = () -> {
            TimeUtil.sleep(0);
            if (Thread.currentThread().isInterrupted() || !reportUid.equals(UserIdMap.getCurrentUid())
                    || generation != TaskLifecycle.generation() || !TaskLifecycle.isOpen()) throw new TaskCancelledException();
        };
        checkpoint.run();
        TaskLifecycle.Work work = TaskLifecycle.enter(generation);
        if (work == null) throw new TaskCancelledException();
        try (TaskLifecycle.Work admitted = work) {
        int requiredSuccesses = eggCount * this.requestsPerEgg;
        final String token = login(checkpoint);
        if (token == null || token.isEmpty()) {
            Log.error("无法获取⚠️有效的Token，放弃上报任务");
            return 0;
        }

        int successfulReports = 0;
        for (int i = 1; i <= requiredSuccesses; i++) {
            checkpoint.run();
            if (!executeSingleReport(gameType, i, requiredSuccesses, channelOverride, checkpoint, token)) {
                break;
            }
            successfulReports++;
            if (i < requiredSuccesses) {
                TimeUtil.sleep(new Random().nextInt(2001) + 1000);
                checkpoint.run();
            }
        }
        return successfulReports;
        }
    }

    /**
     * 执行单次上报请求
     * @param current 当前请求次数
     * @param total 总请求次数
     * @return 是否上报成功
     */
    private boolean executeSingleReport(String gameType, int current, int total) {
        return executeSingleReport(gameType, current, total, null);
    }

    private boolean executeSingleReport(String gameType, int current, int total, String channelOverride) {
        return executeSingleReport(gameType, current, total, channelOverride, () -> { }, this.cachedToken);
    }

    private boolean executeSingleReport(String gameType, int current, int total, String channelOverride, Runnable checkpoint, String token) {
        try {
            checkpoint.run();
            // 同 login()：当前宿主没有 H5HttpUtils，标记必然为空，直接用空串（header 值不变）
            String mark = "";
            String reqId = System.currentTimeMillis() + "_" + (new Random().nextInt(90) + 10); // 10-99随机数

            // 构建请求体
            JSONObject bodyJson = MyUtils.newJSONObject();
            bodyJson.put("v", version);
            bodyJson.put("version", version);
            bodyJson.put("reqId", reqId);
            bodyJson.put("gid", gid);
            bodyJson.put("action_code", action);
            bodyJson.put("action_finish_channel",
                    channelOverride != null && !channelOverride.isEmpty() ? channelOverride : channel);
            String body = bodyJson.toString();

            //Log.other("taskReport 请求体 -> " + body);

            // 建立HTTP连接
            URL url = new URL("https://gamesapi2.aslk2018.com/v2/zfb/taskReport");
            checkpoint.run();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            // 防止外部游戏服连接挂起导致当前线程无限阻塞（reportSync 在主任务线程同步执行）
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("authorization", token);
            conn.setRequestProperty("alipayMiniMark", mark);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", getDynamicUA());
            conn.setRequestProperty("x-release-type", "ONLINE");
            conn.setRequestProperty("referer", "https://" + appId + ".hybrid.alipay-eco.com/" + appId + "/" + version + "/index.html");

            // 写入请求体
            checkpoint.run();
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                checkpoint.run();
                writer.write(body);
            }

            // 处理响应
            int respCode = conn.getResponseCode();
            if (respCode < 200 || respCode >= 300) return false;
            StringBuilder responseText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ))) {
                char[] buffer = new char[2048];
                for (;;) {
                    checkpoint.run();
                    int count = reader.read(buffer);
                    checkpoint.run();
                    if (count < 0) break;
                    if (responseText.length() + count > 65_536) throw new IOException("SDK response exceeds limit");
                    responseText.append(buffer, 0, count);
                }
            }
            checkpoint.run();

            //Log.other("taskReport 响应 -> HTTP " + respCode + " " + responseText);

            // 解析响应
            JSONObject resJson = MyUtils.newJSONObject(responseText.toString());
            if (resJson.optInt("code") == 1) {
                if (current % this.requestsPerEgg == 0) {
                    Log.i("游戏上报进度#" + gameType + "[" + current + "/" + total + "]#SDK已接受");
                }
                return true;
            } else {
                Log.error("游戏SDK未接受上报#次数[" + current + "]#HTTP[" + respCode + "]#code[" + resJson.optInt("code", 0) + "]");
                return false;
            }
            } finally { conn.disconnect(); }
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (IOException e) {
            Log.error("游戏SDK网络异常#次数[" + current + "]#" + e.getClass().getSimpleName());
            return false;
        } catch (Exception e) {
            Log.error("游戏SDK上报异常#次数[" + current + "]#" + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 获取动态User-Agent
     * @return 拼接后的UA字符串
     */
    private String getDynamicUA() {
        String systemUa = System.getProperty("http.agent");
        if (systemUa == null || systemUa.isEmpty()) {
            systemUa = "Mozilla/5.0 (Linux; Android 11)";
        }
        String alipayVer = String.valueOf(ApplicationHook.getAlipayVersion());
        return systemUa + " NebulaSDK/1.8.100112 Nebula AliApp(AP/" + alipayVer + ") AlipayClient/" + alipayVer;
    }
}
