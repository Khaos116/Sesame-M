package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

/** Free bill collage workflow adapted from AG's AntMemberBillBlockWorldWorkflow. */
final class BillBlockWorld {
    private static final String PREFIX = "alipay.memberasset.block.";
    private static final String DONE = "member::billWorldDone";
    private static final String UNKNOWN = "member::billWorldUnknown";
    private static final String RECLAIMED = "member::billWorldReclaimed::";
    private static final String DIRTY = "member::billWorldCanvasDirty";

    private record Block(String id, String config, int level, int width, int length, int x, int y) {
        Block at(int px, int py) { return new Block(id, config, level, width, length, px, py); }
    }
    private record Chapter(String id, boolean completed, String status, String taskStatus, String target, int value) {
        boolean rewarded() { return "REWARDED".equals(status) || "REWARDED".equals(taskStatus); }
        boolean done() { return completed || rewarded() || "COMPLETED".equals(status) || "COMPLETED".equals(taskStatus); }
    }
    private record Prosperity(Integer level, Integer progress, Integer stickerCount) {}
    private record Home(String season, String chapterId, int width, int length, Integer daily, Integer coins,
                        List<Block> pending, List<Block> placed, List<Block> warehouse, List<Chapter> chapters, String detail, Prosperity prosperity) {
        Chapter chapter() {
            for (Chapter c : chapters) if (!c.rewarded() && (c.done() || "IN_PROGRESS".equals(c.status))) return c;
            return chapters.stream().filter(c -> c.id.equals(chapterId)).findFirst().orElse(null);
        }
    }
    private record Action(String operation, JSONObject args, List<Block> positions, Block main, Block merged, Chapter chapter) {}

    static void run(boolean enabled) {
        if (!enabled || DailyTask.skipFlag(DONE, "账单拼贴世界")) return;
        TimeUtil.sleep(0);
        String day = day();
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        if (day.equals(runtime.getString(UNKNOWN))) {
            Log.record("账单拼贴世界：今日动作结果未确认，停止重复提交");
            return;
        }
        try {
            Home home = query();
            boolean needsSync = home != null && home.season.equals(runtime.getString(DIRTY));
            // ponytail: 40 actions per run, 200 records per list and a 64x64 canvas; raise with server evidence.
            for (int step = 0; step < 40 && home != null; step++) {
                TimeUtil.sleep(0);
                if (!day.equals(day())) return;
                Action action = next(home, needsSync);
                if (action == null) {
                    Chapter chapter = home.chapter();
                    if (home.daily != null && home.daily == 0 && home.pending.isEmpty()
                            && home.warehouse.stream().allMatch(b -> reclaimed(b.id))
                            && mergePair(home) == null && chapter != null && chapter.rewarded()) {
                        TimeUtil.sleep(0);
                        Status.flagToday(DONE);
                        Log.other("账单拼贴世界：免费贴纸、画布及章节处理完成");
                    }
                    return;
                }
                TimeUtil.sleep(0);
                if (!runtime.putVerified(UNKNOWN, day)) return;
                if ("reportBlockViewed".equals(action.operation)) {
                    Calendar c = MyUtils.getInstance();
                    JSONObject detailArgs = MyUtils.newJSONObject().put("blockConfigId", home.detail)
                            .put("year", Integer.toString(c.get(Calendar.YEAR)))
                            .put("month", String.format(Locale.ROOT, "%02d", c.get(Calendar.MONTH) + 1));
                    if (request("queryBlockDetail", detailArgs, false) == null) return;
                }
                JSONObject response = request(action.operation, action.args, true);
                Home after = query();
                if (response == null || after == null || !confirmed(home, after, action, response)) {
                    Log.record("账单拼贴世界：动作或回查未确认，今日不再提交");
                    return;
                }
                TimeUtil.sleep(0);
                if ("batchCollectBlock".equals(action.operation) || "placeBlock".equals(action.operation)) {
                    if (!runtime.putVerified(DIRTY, home.season)) return;
                    needsSync = true;
                } else if ("syncCanvas".equals(action.operation)) {
                    if (!runtime.putVerified(DIRTY, "")) return;
                    needsSync = false;
                } else if ("reclaimBlock".equals(action.operation)) {
                    if (!runtime.putVerified(RECLAIMED + action.main.id, day)) return;
                    Status.flagToday(RECLAIMED + action.main.id);
                }
                TimeUtil.sleep(0);
                if (!runtime.putVerified(UNKNOWN, "")) return;
                Log.other("账单拼贴世界：" + action.operation + "回查成功");
                if (!Objects.equals(home.prosperity, after.prosperity) && after.prosperity != null)
                    Log.other("账单拼贴世界：繁荣度回查#等级=" + after.prosperity.level + " 进度=" + after.prosperity.progress + " 贴纸数=" + after.prosperity.stickerCount);
                home = after;
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err("BillBlockWorld", "run", t); }
    }

    private static String day() {
        Calendar c = MyUtils.getInstance();
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static JSONObject request(String operation, JSONObject args, boolean mutation) throws JSONException {
        TimeUtil.sleep(0);
        String rawArgs = "collectDailyProductCoin".equals(operation) ? "[null]" : new JSONArray().put(args).toString();
        String raw = mutation ? ApplicationHook.requestString(PREFIX + operation, rawArgs, 1, 0)
                : ApplicationHook.requestString(PREFIX + operation, rawArgs);
        TimeUtil.sleep(0);
        JSONObject root = MyUtils.newJSONObject(raw);
        Object code = root.opt("resultCode");
        if (!Boolean.TRUE.equals(root.opt("success")) || RpcRequestGuard.isFailure(root)
                || Boolean.FALSE.equals(root.opt("isSuccess"))
                || !(Integer.valueOf(200).equals(integer(root, "resultCode")) || "200".equals(code))) return null;
        JSONObject data = root.optJSONObject("data");
        if (data != null && (RpcRequestGuard.isFailure(data) || Boolean.FALSE.equals(data.opt("success"))
                || Boolean.FALSE.equals(data.opt("isSuccess")) || data.has("resultCode")
                && !(Integer.valueOf(200).equals(integer(data, "resultCode")) || "200".equals(data.opt("resultCode"))))) return null;
        return root;
    }

    private static Home query() throws JSONException {
        JSONObject root = request("queryBlockHome", MyUtils.newJSONObject(), false);
        JSONObject data = root == null ? null : root.optJSONObject("data");
        JSONObject canvas = data == null ? null : data.optJSONObject("canvas");
        if (canvas == null) return null;
        String season = string(canvas, "seasonId"), chapterId = string(canvas, "currentChapterId");
        Integer width = integer(canvas, "canvasWidth"), length = integer(canvas, "canvasLength");
        if (season == null || chapterId == null || width == null || length == null
                || width < 1 || length < 1 || width > 64 || length > 64) return null;
        List<Block> pending = blocks(data.optJSONArray("pendingBlocks"), false);
        List<Block> placed = blocks(data.optJSONArray("placedBlocks"), true);
        List<Chapter> chapters = chapters(data.optJSONArray("chapterTasks"));
        if (pending == null || placed == null || chapters == null || !validCanvas(placed, width, length)) return null;
        JSONObject warehouse = request("queryWarehouseBlocks", MyUtils.newJSONObject().put("seasonId", season), false);
        JSONArray groups = warehouse == null ? null : warehouse.optJSONArray("blocks");
        if (groups == null || groups.length() > 200) return null;
        JSONArray rows = new JSONArray();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            JSONArray records = group == null ? null : group.optJSONArray("blocks");
            if (records == null || records.length() > 200 || rows.length() + records.length() > 200) return null;
            for (int j = 0; j < records.length(); j++) {
                JSONObject record = records.optJSONObject(j);
                String id = record == null ? null : string(record, "blockRecordId");
                if (id == null) return null;
                rows.put(MyUtils.newJSONObject(group.toString()).put("blockRecordId", id));
            }
        }
        List<Block> stored = blocks(rows, false);
        if (stored == null) return null;
        Set<String> ids = new HashSet<>();
        for (List<Block> list : List.of(pending, placed, stored)) for (Block b : list) if (!ids.add(b.id)) return null;
        Integer daily = integer(data, "dailyProductAmt"), coins = integer(data, "coinBalance");
        if (data.has("dailyProductAmt") && daily == null || data.has("coinBalance") && coins == null) return null;
        JSONObject prosperityInfo = data.optJSONObject("prosperityInfo");
        if (data.has("prosperityInfo") && prosperityInfo == null) return null;
        Prosperity prosperity = null;
        if (prosperityInfo != null) {
            Integer level = integer(prosperityInfo, "level"), progress = integer(prosperityInfo, "progress"), count = integer(prosperityInfo, "stickerCount");
            if (prosperityInfo.has("level") && level == null || prosperityInfo.has("progress") && progress == null
                    || prosperityInfo.has("stickerCount") && count == null) return null;
            prosperity = new Prosperity(level, progress, count);
        }
        String detail = "";
        JSONObject normal = data.optJSONObject("normalBlockRes");
        JSONArray details = normal == null ? null : normal.optJSONArray("blockDetailList");
        if (details != null && details.length() <= 200) for (int i = 0; i < details.length(); i++) {
            JSONObject row = details.optJSONObject(i);
            String config = row == null ? null : string(row, "blockConfigId");
            if (config != null) { detail = config; break; }
        }
        return new Home(season, chapterId, width, length, daily, coins, pending, placed, stored, chapters, detail, prosperity);
    }

    private static String string(JSONObject data, String key) {
        Object value = data.opt(key);
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }

    private static Integer integer(JSONObject data, String key) {
        Object value = data.opt(key);
        if (!(value instanceof Number)) return null;
        double n = ((Number) value).doubleValue();
        return Double.isFinite(n) && n >= 0 && n <= Integer.MAX_VALUE && n == Math.rint(n) ? (int) n : null;
    }

    private static List<Block> blocks(JSONArray rows, boolean positioned) {
        if (rows == null || rows.length() > 200) return null;
        List<Block> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            String id = string(row, "blockRecordId"), config = string(row, "blockConfigId");
            Integer level = integer(row, "level"), width = integer(row, "width"), length = integer(row, "length");
            Integer x = positioned ? integer(row, "posX") : Integer.valueOf(-1), y = positioned ? integer(row, "posY") : Integer.valueOf(-1);
            if (id == null || config == null || !ids.add(id) || level == null || level < 1 || width == null
                    || length == null || width < 1 || length < 1 || width > 64 || length > 64 || x == null || y == null) return null;
            result.add(new Block(id, config, level, width, length, x, y));
        }
        return result;
    }

    private static List<Chapter> chapters(JSONArray rows) {
        if (rows == null || rows.length() > 200) return null;
        List<Chapter> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            String id = string(row, "chapterId"), status = string(row, "status");
            if (id == null || !ids.add(id) || status == null || row.has("completed") && !(row.opt("completed") instanceof Boolean)) return null;
            JSONObject task = row.optJSONObject("task");
            String taskStatus = task == null ? "" : string(task, "status"), target = task == null ? "" : string(task, "targetType");
            Integer value = task == null ? 0 : integer(task, "currentValue");
            if (taskStatus == null || target == null || value == null) return null;
            result.add(new Chapter(id, Boolean.TRUE.equals(row.opt("completed")), status.toUpperCase(Locale.ROOT),
                    taskStatus.toUpperCase(Locale.ROOT), target, value));
        }
        return result;
    }

    private static boolean overlaps(Block a, Block b) {
        return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.length && b.y < a.y + a.length;
    }

    private static boolean validCanvas(List<Block> blocks, int width, int length) {
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.x < 0 || b.y < 0 || b.x > width - b.width || b.y > length - b.length) return false;
            for (int j = 0; j < i; j++) if (overlaps(b, blocks.get(j))) return false;
        }
        return true;
    }

    private static Block position(Home h, List<Block> placed, Block candidate, boolean move) {
        // ponytail: bounded first-fit rectangle packing; exact packing only if fragmentation proves a blocker.
        for (int y = 0; y <= h.length - candidate.length; y++) for (int x = 0; x <= h.width - candidate.width; x++) {
            Block b = candidate.at(x, y);
            if (move && x == candidate.x && y == candidate.y) continue;
            if (placed.stream().noneMatch(other -> overlaps(b, other))) return b;
        }
        return null;
    }

    private static Action simple(String operation, JSONObject args, List<Block> positions, Block main, Block merged, Chapter chapter) {
        return new Action(operation, args, positions, main, merged, chapter);
    }

    private static boolean reclaimed(String id) {
        return Status.hasFlagToday(RECLAIMED + id) || day().equals(RuntimeInfo.getInstance().getString(RECLAIMED + id));
    }

    private static Action next(Home h, boolean needsSync) throws JSONException {
        if (h.daily != null && h.daily > 0) return simple("collectDailyProductCoin", MyUtils.newJSONObject(), List.of(), null, null, null);
        if (needsSync) return sync(h, h.placed, null);
        Chapter chapter = h.chapter();
        if (chapter != null && chapter.done() && !chapter.rewarded())
            return simple("advanceChapter", MyUtils.newJSONObject().put("chapterId", chapter.id), List.of(), null, null, chapter);
        Action place = place(h, false);
        if (place != null) return place;
        Action merge = mergePair(h);
        if (merge != null) return merge;
        if (chapter == null || chapter.rewarded()) return null;
        return switch (chapter.target) {
            case "PLACE_BLOCK" -> place(h, true);
            case "MOVE_BLOCK" -> move(h, chapter);
            case "MERGE_COUNT" -> place(h, true);
            case "RECLAIM_BLOCK" -> reclaim(h, chapter);
            case "VIEW_BLOCK_DETAIL" -> h.detail.isEmpty() ? null : simple("reportBlockViewed", MyUtils.newJSONObject(), List.of(), null, null, chapter);
            default -> null;
        };
    }

    private static Action place(Home h, boolean forChapter) throws JSONException {
        List<Block> planned = new ArrayList<>(h.placed), collected = new ArrayList<>(); JSONArray items = new JSONArray();
        for (Block candidate : h.pending) {
            Block b = position(h, planned, candidate, false);
            if (b != null) { collected.add(b); planned.add(b); items.put(blockPosition(b)); }
        }
        if (!collected.isEmpty()) return simple("batchCollectBlock", MyUtils.newJSONObject().put("seasonId", h.season).put("blockItems", items), collected, null, null, null);
        List<Block> candidates = new ArrayList<>(h.pending);
        for (Block candidate : h.warehouse) {
            if (!forChapter && reclaimed(candidate.id)) continue;
            candidates.add(candidate);
            Block b = position(h, h.placed, candidate, false);
            if (b != null) return simple("placeBlock", blockPosition(b), List.of(b), null, null, null);
        }
        for (Block target : candidates) {
            if (target.width > h.width || target.length > h.length) continue;
            List<Block> packed = new ArrayList<>(), ordered = new ArrayList<>(h.placed);
            ordered.add(target);
            ordered.sort(Comparator.comparingInt((Block b) -> b.width * b.length).reversed().thenComparing(b -> b.id));
            for (Block b : ordered) {
                Block pos = position(h, packed, b, false);
                if (pos == null) { packed.clear(); break; }
                packed.add(pos);
            }
            if (packed.size() == h.placed.size() + 1) {
                packed.removeIf(b -> b.id.equals(target.id));
                if (!sameBlocks(packed, h.placed)) return sync(h, packed, null);
            }
            Action merge = mergePair(h);
            if (merge != null) return merge;
            if (h.pending.contains(target)) return reclaim(h, null);
        }
        return null;
    }

    private static Action move(Home h, Chapter chapter) throws JSONException {
        for (Block b : h.placed) {
            List<Block> others = new ArrayList<>(h.placed); others.remove(b);
            Block moved = position(h, others, b, true);
            if (moved != null) { others.add(moved); return sync(h, others, chapter); }
        }
        return null;
    }

    private static JSONObject blockPosition(Block b) throws JSONException {
        return MyUtils.newJSONObject().put("blockRecordId", b.id).put("posX", b.x).put("posY", b.y);
    }

    private static Action sync(Home h, List<Block> blocks, Chapter chapter) throws JSONException {
        if (!validCanvas(blocks, h.width, h.length)) return null;
        JSONArray positions = new JSONArray();
        for (Block b : blocks) positions.put(blockPosition(b));
        return simple("syncCanvas", MyUtils.newJSONObject().put("seasonId", h.season).put("blockPositions", positions), blocks, null, null, chapter);
    }

    private static Action mergePair(Home h) throws JSONException {
        for (int i = 0; i < h.placed.size(); i++) for (int j = i + 1; j < h.placed.size(); j++) {
            Block a = h.placed.get(i), b = h.placed.get(j);
            if (a.config.equals(b.config) && a.level == b.level)
                return simple("mergeBlock", MyUtils.newJSONObject().put("mainBlockId", a.id).put("mergedBlockIds", new JSONArray().put(b.id))
                        .put("posX", b.x).put("posY", b.y), List.of(), a, b, null);
        }
        return null;
    }

    private static Action reclaim(Home h, Chapter chapter) throws JSONException {
        Block b = h.placed.stream().filter(block -> !reclaimed(block.id)).findFirst().orElse(null);
        return b == null ? null : simple("reclaimBlock", MyUtils.newJSONObject().put("blockRecordId", b.id), List.of(), b, null, chapter);
    }

    private static boolean sameBlocks(List<Block> first, List<Block> second) {
        return first.size() == second.size() && new HashSet<>(first).equals(new HashSet<>(second));
    }

    private static Block find(List<Block> blocks, String id) {
        return blocks.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
    }

    private static boolean chapterProgress(Home after, Chapter before) {
        Chapter fresh = after.chapters.stream().filter(c -> c.id.equals(before.id)).findFirst().orElse(null);
        return fresh != null && (fresh.value > before.value || !before.done() && fresh.done());
    }

    private static boolean confirmed(Home before, Home after, Action action, JSONObject response) {
        if (!before.season.equals(after.season) || before.width != after.width || before.length != after.length) return false;
        return switch (action.operation) {
            case "collectDailyProductCoin" -> after.daily != null && after.daily == 0
                    || before.coins != null && after.coins != null && after.coins > before.coins && after.daily != null && after.daily < before.daily;
            case "batchCollectBlock" -> {
                JSONArray results = response.optJSONArray("itemResults");
                if (results == null || results.length() != action.positions.size()) yield false;
                Set<String> ids = new HashSet<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject row = results.optJSONObject(i);
                    if (row == null || string(row, "blockRecordId") == null || !ids.add(row.optString("blockRecordId")) || !"SUCCESS".equals(row.opt("outcome"))
                            || !"PLACED".equals(row.opt("status")) || row.has("failCode") && !"0".equals(row.optString("failCode"))) yield false;
                }
                boolean confirmed = true;
                for (Block b : action.positions) confirmed &= ids.contains(b.id) && b.equals(find(after.placed, b.id)) && find(after.pending, b.id) == null;
                yield confirmed;
            }
            case "placeBlock" -> "PLACED".equals(response.opt("status")) && action.positions.get(0).equals(find(after.placed, action.positions.get(0).id))
                    && find(after.warehouse, action.positions.get(0).id) == null;
            case "syncCanvas" -> sameBlocks(action.positions, after.placed) && (action.chapter == null || chapterProgress(after, action.chapter));
            case "mergeBlock" -> {
                Block main = find(after.placed, action.main.id);
                yield main != null && main.config.equals(action.main.config) && main.level > action.main.level
                        && find(after.placed, action.merged.id) == null && find(after.warehouse, action.merged.id) == null && find(after.pending, action.merged.id) == null;
            }
            case "reclaimBlock" -> find(after.placed, action.main.id) == null && action.main.at(-1, -1).equals(find(after.warehouse, action.main.id))
                    && (action.chapter == null || chapterProgress(after, action.chapter));
            case "advanceChapter" -> {
                Chapter fresh = after.chapters.stream().filter(c -> c.id.equals(action.chapter.id)).findFirst().orElse(null);
                yield fresh != null ? fresh.rewarded() : !after.chapterId.equals(action.chapter.id);
            }
            case "reportBlockViewed" -> chapterProgress(after, action.chapter);
            default -> false;
        };
    }
}
