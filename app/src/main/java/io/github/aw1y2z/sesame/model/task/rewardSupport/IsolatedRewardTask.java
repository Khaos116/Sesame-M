package io.github.aw1y2z.sesame.model.task.rewardSupport;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import io.github.aw1y2z.sesame.BuildConfig;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * Serial execution, request bounds and per-account cooldown for the new
 * (2026-09-14 GR 快照) 批量小额福利任务的公共基类。移植自 GR 分支，见 docs/MyFix.md。
 */
public abstract class IsolatedRewardTask extends ModelTask {
    protected interface Allowed { boolean isAllowed(); }
    private static final ReentrantLock RUN_LOCK = new ReentrantLock();
    private IntegerModelField intervalHours;

    @Override public ModelGroup getGroup() { return ModelGroup.OTHER; }

    @Override public final ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(intervalHours = new IntegerModelField("intervalHours", "查询间隔(小时)", 6, 1, 24));
        addFields(fields);
        return fields;
    }

    protected abstract void addFields(ModelFields fields);
    protected abstract void execute(Run run) throws Exception;

    /** Allows a migrated model to retain its persisted normal query key. */
    protected String nextKey() { return getClass().getSimpleName() + ".nextQuery"; }
    protected String cooldownKey() { return getClass().getSimpleName() + ".cooldownUntil"; }

    protected final RuntimeInfo cooldownState() {
        RuntimeInfo state = RuntimeInfo.getInstance();
        // 旧键无法区分普通间隔与服务端冷却，首次迁移保留其截止时间。
        if (state.getLong(cooldownKey(), -1L) < 0L) {
            state.put(cooldownKey(), state.getLong(nextKey(), 0L));
        }
        return state;
    }

    @Override public final Boolean check() {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return false;
        RuntimeInfo state = cooldownState();
        resetCooldownAfterBuild(uid, state);
        long now = System.currentTimeMillis();
        return isEnable() && RewardRunPolicy.sameAccount(uid, uid)
                && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME
                && RewardRunPolicy.mayQuery(now, state.getLong(nextKey(), 0L))
                && RewardRunPolicy.mayQuery(now, state.getLong(cooldownKey(), 0L));
    }

    /** Resets the normal interval after a build; server rejection cooldowns remain durable. */
    private void resetCooldownAfterBuild(String uid, RuntimeInfo state) {
        if (uid == null || uid.isEmpty()) return;
        String marker = getClass().getSimpleName() + ".cooldownResetVersion";
        if (!BuildConfig.VERSION_NAME.equals(state.getString(marker))) {
            state.clearPrefix(getClass().getSimpleName() + ".attempt.");
            state.put(nextKey(), 0L);
            state.put(marker, BuildConfig.VERSION_NAME);
            Log.record(getName() + "：新版本首次运行，已重置查询间隔，保留服务端冷却");
        }
    }

    @Override public final void run() {
        String queuedAccount = UserIdMap.getCurrentUid();
        boolean locked = false;
        try {
            RUN_LOCK.lockInterruptibly();
            locked = true;
            if (!RewardRunPolicy.sameAccount(queuedAccount, UserIdMap.getCurrentUid()) || !check()) return;
            Run run = new Run(queuedAccount, RuntimeInfo.getInstance());
            run.requireCurrent();
            run.state.put(nextKey(), System.currentTimeMillis() + intervalHours.getValue() * 3_600_000L);
            execute(run);
        } catch (Stopped ignored) {
            // A bounded, failed or interrupted run has already logged its reason.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.record(getName() + "：本轮结束，异常类型=" + e.getClass().getSimpleName());
        } finally {
            if (locked) RUN_LOCK.unlock();
        }
    }

    protected final class Run {
        private final String account;
        private final RuntimeInfo state;
        private int requests;
        private long lastCallNanos;

        private Run(String account, RuntimeInfo state) { this.account = account; this.state = state; }

        private void requireCurrent() throws Stopped {
            if (Thread.currentThread().isInterrupted() || !isEnable() || ApplicationHook.isOffline()
                    || !RewardRunPolicy.sameAccount(account, UserIdMap.getCurrentUid())) throw new Stopped();
        }

        public JSONObject query(String method, String args) throws Exception {
            return call(method, args, () -> true);
        }

        private JSONObject call(String method, String args, Allowed allowed) throws Exception {
            requireCurrent();
            if (!allowed.isAllowed()) throw new Stopped();
            if (!RequestBudgetPolicy.mayRequest(requests, RequestBudgetPolicy.MAX_REQUESTS)) throw new Stopped();
            long pause = RequestBudgetPolicy.pacingMs(lastCallNanos, System.nanoTime());
            if (pause > 0) Thread.sleep(pause);
            requireCurrent();
            if (!allowed.isAllowed()) throw new Stopped();
            requests++;
            String raw;
            try {
                // Do not let the bridge retry an uncertain reward operation.
                raw = ApplicationHook.requestString(method, args, 1, -1);
            } finally {
                lastCallNanos = System.nanoTime();
            }
            requireCurrent();
            if (raw == null || raw.isEmpty()) {
                Log.record(getName() + "：无响应，停止本轮 method=" + method);
                throw new Stopped();
            }
            JSONObject result = new JSONObject(raw);
            boolean denied = RpcFailurePolicy.isRiskDenied(
                    result.optInt("error", 0) == 1009 ? "1009" : result.optString("error", ""),
                    result.optString("errorMessage", ""));
            if (denied) {
                state.put(cooldownKey(), RequestBudgetPolicy.cooldownUntil(System.currentTimeMillis(),
                        RpcFailurePolicy.RISK_DENIED_MS));
                Log.record(getName() + "：访问被拒绝，暂停24小时 method=" + method);
                throw new Stopped();
            }
            if (result.optInt("error", 0) != 0 || !Boolean.TRUE.equals(result.opt("success"))) {
                String code = result.has("error") ? result.optString("error")
                        : result.optString("resultCode", "");
                String message = RpcFailurePolicy.boundedMessage(result.optString("errorMessage",
                        result.optString("errorMsg", result.optString("resultDesc", ""))));
                RpcFailurePolicy.Kind kind = RpcFailurePolicy.kind(code);
                long cooldown = RpcFailurePolicy.cooldownMs(kind);
                if (cooldown > 0L) {
                    state.put(cooldownKey(), RequestBudgetPolicy.cooldownUntil(
                            System.currentTimeMillis(), cooldown));
                }
                if (kind == RpcFailurePolicy.Kind.BUSINESS_REJECTED) {
                    Log.record(getName() + "：抓取资格/窗口未满足，暂停6小时 method=" + method
                            + " code=" + code + (message.isEmpty() ? "" : " message=" + message));
                } else if (kind == RpcFailurePolicy.Kind.SYSTEM_ERROR) {
                    Log.record(getName() + "：服务端暂时不可用，暂停12小时 method=" + method
                            + " code=" + code + (message.isEmpty() ? "" : " message=" + message));
                } else {
                    Log.record(getName() + "：响应未明确成功，停止本轮 method=" + method
                            + " code=" + code + (message.isEmpty() ? "" : " message=" + message));
                }
                throw new Stopped();
            }
            return result;
        }

        /** At most one mutation attempt per account/action/calendar day (GMT+8), including timeouts. */
        public JSONObject onceToday(String action, String method, String args, Allowed allowed) throws Exception {
            requireCurrent();
            if (!allowed.isAllowed()) throw new Stopped();
            if (!RequestBudgetPolicy.mayRequest(requests, RequestBudgetPolicy.MAX_REQUESTS)) throw new Stopped();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            String day = format.format(new Date());
            String key = getClassKey() + ".attempt." + action;
            if (!RewardRunPolicy.mayAttempt(day, state.getString(key))) {
                Log.record(getName() + "：今日已尝试该动作，等待下一日");
                return null;
            }
            state.put(key, day);
            return call(method, args, allowed);
        }

        private String getClassKey() { return IsolatedRewardTask.this.getClass().getSimpleName(); }
    }

    private static final class Stopped extends Exception { }
}
