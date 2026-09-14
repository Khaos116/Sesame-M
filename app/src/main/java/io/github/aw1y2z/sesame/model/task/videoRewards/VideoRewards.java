package io.github.aw1y2z.sesame.model.task.videoRewards;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;

/** 视频红包钱包查询：只记录页面真实观察到的播放。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoRewards extends IsolatedRewardTask {
    /** 观察快照到模型消费的最大延迟；超过即视为过期证据。 */
    static final long EVIDENCE_FRESH_MS = 10 * 60_000L;
    private IntegerModelField minimumPlaybackSeconds;
    private BooleanModelField reserve;
    private BooleanModelField inspectTasks;
    private BooleanModelField claimWallet;
    private BooleanModelField watchRecord;

    @Override public String getName() { return "视频红包钱包查询"; }
    @Override protected String nextKey() { return "VideoRewards.nextWalletQuery"; }

    // 观察者（VideoPageObserver）读取的实时开关入口；Model 未启用时观察者完全静止。

    /** 页面观察采样是否被请求：主开关与真实观看记录开关同时开启。 */
    public static boolean watchSamplingRequested() {
        VideoRewards model = io.github.aw1y2z.sesame.data.Model.getModel(VideoRewards.class);
        return model != null && model.isEnable()
                && model.watchRecord != null && model.watchRecord.getValue();
    }

    /** 配置的最小真实播放时长（毫秒），默认 15 秒。 */
    public static long minimumWatchMs() {
        VideoRewards model = io.github.aw1y2z.sesame.data.Model.getModel(VideoRewards.class);
        if (model == null || model.minimumPlaybackSeconds == null) return 15_000L;
        return model.minimumPlaybackSeconds.getValue() * 1000L;
    }

    /** 观察者请求提前调度；账号和实时开关仍有效时清空普通间隔并唤醒主调度器。 */
    public static boolean expediteNextQuery(String account) {
        if (!watchSamplingRequested() || account == null
                || !account.equals(UserIdMap.getCurrentUid())) return false;
        VideoRewards model = io.github.aw1y2z.sesame.data.Model.getModel(VideoRewards.class);
        if (model == null) return false;
        model.cooldownState().put("VideoRewards.nextWalletQuery", 0L);
        ApplicationHook.requestEarlyTaskRun();
        return true;
    }

    @Override protected void addFields(ModelFields fields) {
        fields.addField(minimumPlaybackSeconds = new IntegerModelField(
                "minimumPlaybackSeconds", "最小真实播放时长(秒)", 15, 1, 3600));
        fields.addField(reserve = new BooleanModelField("reserve", "预约可用视频任务", false));
        fields.addField(inspectTasks = new BooleanModelField("inspectTasks", "查询动态视频任务详情", false));
        fields.addField(claimWallet = new BooleanModelField("claimWallet", "领取服务端明确可领取红包", false));
        fields.addField(watchRecord = new BooleanModelField("watchRecord", "真实观看后报告观看记录", false));
    }

    /** 查询一页，并按配置每日最多预约一次，全部走 Run 传输层的守护逻辑。 */
    @Override protected void execute(Run run) throws Exception {
        JSONObject result = run.query(VideoRewardsRpcCall.WALLET_METHOD, VideoRewardsRpcCall.WALLET_ARGS);
        JSONArray groups = result.optJSONArray("envelopeDetailList");
        if (groups == null || groups.length() > 20) {
            Log.record(getName() + "：红包列表缺失或超出范围，本轮结束");
            return;
        }
        int entries = 0, progressing = 0, unknown = 0;
        boolean more = false;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) {
                Log.record(getName() + "：红包分组格式不明确，本轮结束");
                return;
            }
            more |= group.optBoolean("hasMore", false);
            JSONArray list = group.optJSONArray("envelopeVOList");
            if (list == null) continue;
            if (list.length() > 100 - entries) {
                Log.record(getName() + "：红包条目超出范围，本轮结束");
                return;
            }
            for (int j = 0; j < list.length(); j++) {
                entries++;
                JSONObject item = list.optJSONObject(j);
                if (item != null && "progressing".equals(item.optString("status"))) progressing++;
                else unknown++;
            }
        }
        Log.record(getName() + "：首屏红包=" + entries + "，进行中=" + progressing
                + "，其他或未知状态=" + unknown + "，还有分页=" + more);
        if (claimWallet.getValue()) {
            VideoWalletRewardProtocol.Claim claim = VideoWalletRewardProtocol.findClaim(result.toString());
            if (claim == null) {
                Log.record(getName() + "：没有服务端明确可领取的钱包红包，本轮不发送领取请求");
            } else {
                JSONObject claimed = run.onceToday("walletClaim:" + claim.activityId,
                        VideoWalletRewardProtocol.METHOD,
                        VideoWalletRewardProtocol.arguments(claim), () -> claimWallet.getValue());
                if (claimed != null) Log.record(getName() + "：钱包红包领取接口返回成功，服务端到账结果待核对");
            }
        }
        boolean needTasks = inspectTasks.getValue() || watchRecord.getValue() || reserve.getValue();
        if (!needTasks) return;
        JSONObject tasksResponse = run.query(VideoTaskQueryProtocol.METHOD, VideoTaskQueryProtocol.ARGS);
        java.util.List<VideoTaskDetail> tasks = VideoTaskQueryProtocol.parse(tasksResponse.toString());
        Log.record(getName() + "：动态视频任务详情=" + tasks.size()
                + "，仅保留未完成且带rewardParams的条目");
        if (watchRecord.getValue()) {
            recordObservedWatch(run, tasks);
        }
        if (!reserve.getValue()) return;
        long legacyAttempt = RuntimeInfo.getInstance().getLong("VideoRewards.reserveAttempt", 0L);
        if (VideoReservationPolicy.blocksLegacyAttempt(System.currentTimeMillis(), legacyAttempt)) {
            Log.record("视频红包：已有当日预约记录，本轮跳过");
            return;
        }
        JSONObject reserved = run.onceToday("reserve", VideoRewardsRpcCall.RESERVE_METHOD,
                VideoRewardsRpcCall.RESERVE_ARGS, () -> reserve.getValue());
        if (reserved != null) Log.record("视频红包：预约接口返回成功");
    }

    /**
     * 消费页面观察快照：同一 contentId、服务端时长交叉校验且达到阈值才发送一次
     * vv.record；全部参数（contentId、sourcePage、rewardParams）来自服务端与观察值，
     * 不合成任何字段。
     */
    private void recordObservedWatch(Run run, java.util.List<VideoTaskDetail> tasks) {
        String account = UserIdMap.getCurrentUid();
        if (account == null || account.isEmpty()) {
            Log.record(getName() + "：账号不可用，本轮不发送观看记录");
            return;
        }
        com.fasterxml.jackson.databind.JsonNode page = VideoWatchEvidence.Store.takeFresh(
                account, System.currentTimeMillis(), EVIDENCE_FRESH_MS);
        if (page == null) {
            Log.record(getName() + "：没有新鲜的页面观察快照，本轮不发送观看记录");
            return;
        }
        long minimumMs = minimumPlaybackSeconds == null
                ? 15_000L : minimumPlaybackSeconds.getValue() * 1000L;
        for (VideoTaskDetail task : tasks) {
            VideoWatchEvidence evidence = VideoWatchEvidence.evaluate(
                    page, task, minimumMs, account, System.currentTimeMillis());
            if (evidence == null) continue;
            try {
                String args = VideoRecordProtocol.arguments(
                        task.contentId, evidence.sourcePage, task.rewardParams);
                JSONObject recorded = run.onceToday("vv.record:" + task.contentId,
                        VideoRecordProtocol.METHOD, args, () -> watchRecord.getValue());
                VideoWatchEvidence.Store.clear();
                if (recorded != null) {
                    VideoRecordOutcome outcome = VideoRecordOutcome.parse(recorded.toString());
                    Log.record(getName() + "：真实观看记录接口返回成功 contentId长度=" + task.contentId.length()
                            + " 观察播放=" + (evidence.currentMs / 1000) + "秒"
                            + " 服务端奖励状态=" + outcome.rewardState
                            + (outcome.contentId.isEmpty() ? "" : " 回显contentId长度=" + outcome.contentId.length())
                            + "，待下一轮查询核对");
                }
                return;
            } catch (Exception malformed) {
                Log.record(getName() + "：观看记录参数构造被拒绝，停止本轮");
                return;
            }
        }
        Log.record(getName() + "：页面快照未匹配到可记录的未完成任务，本轮不发送");
    }
}
