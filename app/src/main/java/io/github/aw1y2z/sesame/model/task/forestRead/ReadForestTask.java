package io.github.aw1y2z.sesame.model.task.forestRead;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.hook.AlipayMiniMarkHelper;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.model.task.forestRead.ReadForestRpcCall.ReadFailure;

/** 同步阅读任务，使用 M 的账号准入、当日状态和持久化记录。 */
public final class ReadForestTask {
    private static final String DONE = "forest::readForest::full";
    private static final String FINISHED = "readForest.finishedBooks";

    private ReadForestTask() { }

    public static void execute() {
        String stage = "授权";
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) {
                log("无纸阅读：跳过，账号切换/初始化中");
                return;
            }
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) {
                log("无纸阅读：跳过，当前账号标识为空");
                return;
            }
            if (Status.hasFlagToday(DONE)) {
                log("无纸阅读：跳过，今日已成功达到满额");
                return;
            }
            log("无纸阅读：开始，获取授权码并自动换取 Token");
            String code = AuthCodeHelper.getAuthCode(ReadForestRpcCall.APP_ID);
            if (code == null || code.trim().isEmpty()) {
                log("无纸阅读：授权失败，未取得授权码；原因=" + AuthCodeHelper.getFailureReason());
                tokenHelp();
                return;
            }
            ReadForestRpcCall client = new ReadForestRpcCall(AlipayMiniMarkHelper.getAlipayMiniMark(
                    ReadForestRpcCall.APP_ID, ReadForestRpcCall.VERSION));
            stage = "登录";
            client.login(code);
            log("无纸阅读：登录成功，已自动获取 Token（仅供本轮使用）");
            stage = "查询能量进度";
            JSONObject progress = progress(client);
            if (full(progress, uid)) return;
            int current = progress.optInt("current");
            int initial = current;
            log("无纸阅读：当前能量进度 " + current + "/" + progress.optInt("total"));
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            JSONObject finished = MyUtils.newJSONObject(runtime.getString(FINISHED));
            stage = "查询书籍";
            JSONArray blocks = client.index().optJSONArray("data");
            if (blocks == null) throw new ReadFailure("首页缺少书籍列表");
            Set<String> visited = new HashSet<>();
            int count = 0, stagnant = 0;
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                JSONArray books = block == null ? null : block.optJSONArray("block_resource");
                if (books == null) continue;
                for (int j = 0; j < books.length(); j++) {
                    JSONObject book = books.optJSONObject(j);
                    if (book == null || !"0.00".equals(book.opt("price"))) continue;
                    String bid = id(book.opt("book_id"));
                    if (bid.isEmpty() || "0".equals(bid) || "null".equals(bid) || finished.has(bid) || !visited.add(bid)) continue;
                    stage = "查询章节";
                    JSONObject chapter = client.chapter(bid, "").optJSONObject("data");
                    Set<String> chapters = new HashSet<>();
                    while (true) {
                        if (chapter == null) throw new ReadFailure("缺少章节数据");
                        String cid = id(chapter.opt("id"));
                        if (cid.isEmpty() || "0".equals(cid) || "null".equals(cid) || !chapters.add(cid)) {
                            throw new ReadFailure("章节标识无效或循环");
                        }
                        if (!chapter.has("next_id")) throw new ReadFailure("章节缺少 next_id");
                        Object nextValue = chapter.opt("next_id");
                        String next = nextValue == JSONObject.NULL ? "" : id(nextValue);
                        if (next.isEmpty() && nextValue != JSONObject.NULL && !"".equals(nextValue)) {
                            throw new ReadFailure("下一章节类型无效");
                        }
                        // ponytail: 每轮最多 30 章，避免占用森林线程无限连读；有实测需求再调整。
                        if (count++ >= 30) {
                            log("无纸阅读：本轮停止，达到 30 章上限；今日 " + current + "/" + progress.optInt("total") + "，尚未满额");
                            return;
                        }
                        stage = "上报阅读";
                        client.read(bid, cid, chapter.optString("name", ""), RandomUtil.nextInt(333, 1001));
                        TimeUtil.sleep(1000);
                        stage = "查询能量进度";
                        progress = progress(client);
                        int after = progress.optInt("current");
                        if (after > current) {
                            log("无纸阅读：成功获得能量 +" + (after - current) + "，今日 " + after + "/" + progress.optInt("total"));
                            stagnant = 0;
                        } else stagnant++;
                        if (full(progress, uid)) return;
                        current = after;
                        if (stagnant >= 5) {
                            log("无纸阅读：本轮停止，连续 5 章能量未增加；今日 " + current + "/" + progress.optInt("total")
                                    + "，尚未满额；服务端未说明原因，不记今日完成");
                            return;
                        }
                        if (next.isEmpty() || "null".equals(next) || "0".equals(next)) {
                            finished.put(bid, book.optString("name", ""));
                            runtime.put(FINISHED, finished.toString());
                            break;
                        }
                        TimeUtil.sleep(RandomUtil.nextInt(1000, 3002));
                        stage = "查询章节";
                        chapter = client.chapter(bid, next).optJSONObject("data");
                    }
                }
            }
            log("无纸阅读：本轮结束，处理 " + count + " 章、能量增加 " + Math.max(0, current - initial)
                    + "；首页暂无更多未读免费书籍，今日 " + current + "/" + progress.optInt("total") + "，尚未满额");
        } catch (TaskCancelledException e) {
            log("无纸阅读：已取消，任务停止/代际作废，不记今日完成");
            throw e;
        } catch (Exception e) {
            // 不记录原始请求、响应或宿主异常，以免泄露授权码/token。
            log("无纸阅读：" + stage + "失败，本轮停止；原因=" + ReadForestRpcCall.failureReason(e));
            if ("授权".equals(stage) || "登录".equals(stage) || (e instanceof ReadFailure && ((ReadFailure) e).needsLogin)) {
                tokenHelp();
            }
        }
    }

    /** 这项功能的状态同时写运行日志与森林分类，不改变其它模块的日志规则。 */
    static void log(String message) {
        Log.record(message);
        Log.forest(message);
    }

    private static void tokenHelp() {
        log("无纸阅读：获取/刷新 Token：支付宝 → 搜索并打开无纸阅读小程序（appId=" + ReadForestRpcCall.APP_ID
                + "）→ 完成正常登录/授权 → 返回并等待或重新运行森林任务。M 自动换取 Token，无需手动复制或填写。"
                + "若仍提示宿主授权服务不兼容，需要反馈运行日志，反复登录不能保证解决。");
    }

    private static String id(Object value) {
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof Number && ((Number) value).doubleValue() > 0
                && ((Number) value).doubleValue() == ((Number) value).longValue()) {
            return Long.toString(((Number) value).longValue());
        }
        // 服务端用数值 0 表示最后一章。
        return Integer.valueOf(0).equals(value) || Long.valueOf(0).equals(value) ? "0" : "";
    }

    private static JSONObject progress(ReadForestRpcCall client) throws Exception {
        JSONObject data = client.energy().optJSONObject("data");
        if (data == null || !validInt(data.opt("current"), 0) || !validInt(data.opt("total"), 1)) {
            throw new ReadFailure("能量进度缺少有效 current/total，不能确认今日完成");
        }
        return data;
    }

    private static boolean validInt(Object value, int min) {
        if (!(value instanceof Number)) return false;
        double number = ((Number) value).doubleValue();
        return number >= min && number <= Integer.MAX_VALUE && number == ((Number) value).longValue();
    }

    private static boolean full(JSONObject progress, String uid) {
        if (progress.optInt("current") < progress.optInt("total")) return false;
        Status.flagToday(DONE, uid);
        log("无纸阅读：成功，服务端确认今日满额 " + progress.optInt("current") + "/" + progress.optInt("total"));
        return true;
    }
}
