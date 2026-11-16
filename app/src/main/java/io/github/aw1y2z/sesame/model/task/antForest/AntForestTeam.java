package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestTeam {

    private static final String TAG = AntForestTeam.class.getSimpleName();
    private final AntForestV2 host;

    AntForestTeam(AntForestV2 host) {
        this.host = host;
    }

    void teamCooperateWater() {
        // 记录本次是否由本方法切到组队模式，只要是就得切回来
        boolean switchedToTeam = false;
        try {
            int userDailyTarget = Math.min(Math.max(host.partnerteamWaterNum.getValue(), 10), 5000);
            int todayUsed = Status.getforestHuntHelpToday(AntForestV2.FLAG_TEAM_WATER_DAILY_COUNT);
            int userRemainingQuota = userDailyTarget - todayUsed;

            if (userRemainingQuota < 10) {
                Log.record("组队合种今日已达标 (已浇" + todayUsed + "g / 目标" + userDailyTarget + "g)，跳过");
                return;
            }

            JSONObject homeJo = queryTeamHomePage();
            if (homeJo == null) {
                return;
            }

            // 必须先把模式切到组队，个人模式的返回体里没有 teamHomeResult，取不到 teamId
            int state = teamState(homeJo);
            if (state < 0) {
                // 无法判定组队状态（字段缺失/异常）：不主动改账户设置，直接跳过
                Object nextAction = homeJo.opt("nextAction");
                String actionInfo;
                if (nextAction == null) actionInfo = "<缺失>";
                else if (nextAction == JSONObject.NULL) actionInfo = "<null>";
                else if (nextAction instanceof String && ((String) nextAction).matches("[A-Za-z0-9_.-]{1,64}")) actionInfo = (String) nextAction;
                else actionInfo = nextAction instanceof String ? "<非标准字符串>" : "<类型异常>";
                JSONObject teamHome = homeJo.optJSONObject("teamHomeResult");
                Log.record("无法判定组队状态，跳过组队合种浇水（nextAction=" + actionInfo
                        + "，teamHomeResult对象=" + (teamHome != null)
                        + "，mainMember对象=" + (teamHome != null && teamHome.optJSONObject("mainMember") != null) + "）");
                return;
            }
            if (state == 0) {
                Log.record("当前为个人模式，尝试切换至组队浇水");
                if (!updateUserConfiginTeam(true)) {
                    Log.record("切换到组队模式失败，跳过组队合种浇水");
                    return;
                }
                switchedToTeam = true;
                Status.flagToday(AntForestV2.FLAG_TEAM_MODE_SWITCHED);
                homeJo = queryTeamHomePage();
                if (homeJo == null) {
                    return;
                }
                if (!isTeam(homeJo)) {
                    Log.record("切换后仍未进入组队版(可能未加入队伍)，跳过组队合种浇水");
                    return;
                }
            }

            String teamId = getTeamId(homeJo);
            if (teamId.isEmpty()) {
                Log.record("未获取到组队合种 TeamID");
                return;
            }

            JSONObject userBaseInfo = homeJo.optJSONObject("userBaseInfo");
            int currentEnergy = userBaseInfo == null ? 0 : userBaseInfo.optInt("currentEnergy", 0);
            if (currentEnergy < 10) {
                Log.record("当前能量不足10g(" + currentEnergy + "g)，无法浇水");
                return;
            }

            // 获取服务端限制
            JSONObject miscJo = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo("teamCanWaterCount", teamId));
            if (!MessageUtil.checkResultCode(TAG, miscJo)) {
                Log.record("queryMiscInfo 查询失败");
                return;
            }

            int serverRemaining = getTeamCanWaterCount(miscJo);
            Log.record("组队状态检查:目标剩余" + userRemainingQuota + "g|官方剩余" + serverRemaining + "g|背包能量" + currentEnergy + "g");

            if (serverRemaining < 10) {
                Log.record("官方限制今日无可浇水额度，跳过");
                return;
            }

            // 计算最终浇水量
            int finalWaterAmount = Math.min(userRemainingQuota, Math.min(serverRemaining, currentEnergy));
            if (finalWaterAmount < 10) {
                Log.record("计算后浇水量(" + finalWaterAmount + "g)低于最小限制10g，不执行");
                return;
            }

            if (ApplicationHook.isOffline()) return;
            // 先保留额度：RPC返回异常或取消也可能发生在服务端已扣能量之后。
            Status.forestHuntHelpToday(AntForestV2.FLAG_TEAM_WATER_DAILY_COUNT, todayUsed + finalWaterAmount, UserIdMap.getCurrentUid());
            JSONObject waterJo = MyUtils.newJSONObject(AntForestRpcCall.teamWater(teamId, finalWaterAmount));
            if (MessageUtil.checkResultCode(TAG, waterJo) || MessageUtil.checkSuccess(TAG, waterJo)) {
                Log.forest("组队合种🚿给合种浇水" + finalWaterAmount + "g");
                Toast.show("组队合种🚿给合种浇水" + finalWaterAmount + "g");
            } else {
                Log.record("组队合种浇水未确认成功，保留额度避免重复：" + waterJo);
            }
            Log.record("组队合种今日浇水累计: " + (todayUsed + finalWaterAmount) + "g / " + userDailyTarget + "g");
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "teamCooperateWater err:", t);
        } finally {
            // 任何分支（含异常和提前 return）都要切回个人模式，否则会一直停在组队模式影响其它功能
            if (switchedToTeam) {
                if (updateUserConfiginTeam(false)) {
                    Status.clearFlag(AntForestV2.FLAG_TEAM_MODE_SWITCHED);
                    Log.record("已返回个人模式");
                } else {
                    Log.record("组队合种浇水后切回个人模式失败，下次运行重试");
                }
            }
        }
    }

    /** 查询森林首页，失败返回 null */
    static JSONObject queryTeamHomePage() {
        try {
            JSONObject homeJo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                Log.record("queryHomePage 返回异常");
                return null;
            }
            return homeJo;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "queryTeamHomePage err:", t);
        }
        return null;
    }

    /** 取组队合种的 teamId，缺失时返回空串 */
    static String getTeamId(JSONObject homeJo) {
        JSONObject teamHomeResult = homeJo.optJSONObject("teamHomeResult");
        if (teamHomeResult == null) {
            return "";
        }
        JSONObject teamBaseInfo = teamHomeResult.optJSONObject("teamBaseInfo");
        return teamBaseInfo == null ? "" : teamBaseInfo.optString("teamId", "");
    }

    /** 取官方今日剩余可浇额度，缺失时返回 0 */
    static int getTeamCanWaterCount(JSONObject miscJo) {
        JSONObject combineHandlerVOMap = miscJo.optJSONObject("combineHandlerVOMap");
        if (combineHandlerVOMap == null) {
            return 0;
        }
        JSONObject teamCanWaterCount = combineHandlerVOMap.optJSONObject("teamCanWaterCount");
        return teamCanWaterCount == null ? 0 : teamCanWaterCount.optInt("waterCount", 0);
    }

    /**
     * 组队合种浇水会临时把账号切到组队模式。若上次切回失败、或进程在切回前被杀，
     * 这里做一次校正，避免用户被永久留在组队模式（会影响个人主页 / 好友页的表现）。
     * <p>
     * 没开「组队合种浇水」也会执行：正是"开了功能 → 切换中途中断 → 用户随后把开关关掉"这种情况最需要校正。
     */
    static void fixTeamModeIfNeeded() {
        if (!Status.hasFlagToday(AntForestV2.FLAG_TEAM_MODE_SWITCHED)) {
            return;
        }
        if (updateUserConfiginTeam(false)) {
            Status.clearFlag(AntForestV2.FLAG_TEAM_MODE_SWITCHED);
            Log.record("检测到上次组队合种浇水未切回，已恢复个人模式");
        } else {
            Log.record("上次组队合种浇水未切回个人模式，本次恢复失败，下次运行重试");
        }
    }

    static boolean updateUserConfiginTeam(Boolean needReturn) {
        try {
            String updateStr = AntForestRpcCall.updateUserConfiginTeam(needReturn);
            JSONObject updateJo = MyUtils.newJSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("森林模式切换失败：目标=" + (needReturn ? "组队" : "个人")
                        + "，code=" + updateJo.optString("resultCode", "缺失"));
                return false;
            } else {
                return true;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable th) {
            Log.err(TAG, "updateUserConfig err:", th);
        }
        return false;
    }

    /** mainMember确认组队；个人入口仅允许Team/Cultivate，新取值需人工核实后加入。 */
    static int teamState(JSONObject homeObj) {
        if (homeObj == null) return -1;
        JSONObject teamHomeResult = homeObj.optJSONObject("teamHomeResult");
        if (teamHomeResult != null && teamHomeResult.optJSONObject("mainMember") != null) return 1;
        String nextAction = homeObj.optString("nextAction", "");
        if ("Cultivate".equals(nextAction) || "Team".equals(nextAction)) {
            return 0;
        }
        return -1;
    }

    static boolean isTeam(JSONObject homeObj) {
        return teamState(homeObj) == 1;
    }

    static void loveteam(int waterNum) {
        if (ApplicationHook.isOffline() || Status.hasFlagToday(AntForestV2.FLAG_LOVETEAM_WATER)) return;
        String stage = "查询浇水队伍";
        try {
            TimeUtil.sleep(0);
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.loveteamHome());
            if (jo.optJSONObject("resData") != null) jo = jo.optJSONObject("resData");
            if (!MessageUtil.checkResultCode(TAG, jo)) return;
            JSONObject userInfo = jo.optJSONObject("userInfo");
            String teamId = userInfo == null ? "" : userInfo.optString("teamId", "");
            if (teamId.isEmpty()) {
                Log.record("真爱合种:未加入真爱合种或未取到队伍，跳过");
                return;
            }
            stage = "真爱浇水";
            loveteamWater(teamId, getLoveteamName(jo, teamId), waterNum);
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable th) {
            Log.record("真爱合种[" + stage + "]异常：" + th.getClass().getSimpleName() + "，保留已有浇水防重标记");
            Throwable diagnostic = new Throwable(th.getClass().getName());
            diagnostic.setStackTrace(th.getStackTrace());
            Log.printStackTrace(TAG, diagnostic);
        }
    }

    /**
     * 真爱合种名称：loveHome 返回体里没有稳定已知的名称字段，这里按可能的 key 依次探测，
     * 都取不到时退回 teamId，保证日志始终有可读内容。
     */
    static String getLoveteamName(JSONObject loveHome, String teamId) {
        JSONObject userInfo = loveHome.optJSONObject("userInfo");
        if (userInfo != null) {
            for (String key : new String[]{"teamName", "name", "treeName"}) {
                String name = userInfo.optString(key, "");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        String name = loveHome.optString("teamName", "");
        return name.isEmpty() ? teamId : name;
    }

    /** 无效响应不能按永久失败记账；两个浇水接口都需明确的状态字段。 */
    static boolean hasWaterResult(JSONObject jo) {
        if (jo.opt("success") instanceof Boolean || jo.opt("isSuccess") instanceof Boolean) return true;
        for (String key : new String[]{"resultCode", "code", "errorCode"}) {
            Object code = jo.opt(key);
            if ((code instanceof String || code instanceof Number) && !jo.optString(key).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 真爱合种浇水（每日一次）。
     * <p>当日标记**先落再发请求**，离线时不发请求、不落标记；请求发起后不撤销标记。
     * <p>响应判定不成、超时、响应解析异常等一律**保留标记**：请求可能已经到服务端（水已经浇了），
     * 撤销标记会让「改配置触发的那一轮」再浇一次，表现为"每次修改配置返回就再浇一次"。
     */
    static void loveteamWater(String teamId, String teamName, int waterNum) {
        if (ApplicationHook.isOffline()) return;
        Status.flagToday(AntForestV2.FLAG_LOVETEAM_WATER);
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.loveteamWater(teamId, waterNum));
            if (!hasWaterResult(jo)) {
                Log.record("真爱合种浇水响应为空或无效，保留标记避免重复");
                return;
            }
            if (MessageUtil.checkSuccess(TAG, jo) || MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                Toast.show("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                return;
            }
            // 判定不成（结构不符 / 今日已浇过 / 额度用尽）当日不再重发，保留响应原文便于排查
            Log.record("真爱合种浇水未确认成功，今日不再尝试：" + jo);
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable th) {
            Log.err(TAG, "loveteamWater err:", th);
        }
    }
}
