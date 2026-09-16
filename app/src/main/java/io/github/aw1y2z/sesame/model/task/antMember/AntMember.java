package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;

import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAntMemberTaskList;
import io.github.aw1y2z.sesame.entity.AlipayMemberCreditSesameTaskList;
import io.github.aw1y2z.sesame.entity.MemberBenefit;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle;
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard;
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MemberBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class AntMember extends ModelTask {
    private static final String TAG = AntMember.class.getSimpleName();
    
    @Override
    public String getName() {
        return "会员";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }
    
    private BooleanModelField AntMemberTask;
    private BooleanModelField AutoAntMemberTaskList;
    private SelectModelField AntMemberTaskList;
    private BooleanModelField memberSign;
    private BooleanModelField memberPointExchangeBenefit;
    private SelectModelField memberPointExchangeBenefitList;
    
    private BooleanModelField collectSesame;
    private BooleanModelField AutoMemberCreditSesameTaskList;
    private SelectModelField MemberCreditSesameTaskList;
    private BooleanModelField SesameGrowthBehavior;
    private BooleanModelField promise;
    private SelectModelField promiseList;
    private BooleanModelField enableGameCenter;
    private BooleanModelField KuaiDiFuLiJia;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AntMemberTask = new BooleanModelField("AntMemberTask", "会员任务", false));
        modelFields.addField(AutoAntMemberTaskList = new BooleanModelField("AutoAntMemberTaskList", "会员任务 | 自动黑白名单", true));
        modelFields.addField(AntMemberTaskList = new SelectModelField("AntMemberTaskList", "会员任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntMemberTaskList::getList));
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 领取", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒任务 | 自动黑白名单", true));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒任务 | 黑名单列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList));
        modelFields.addField(SesameGrowthBehavior = new BooleanModelField("SesameGrowthBehavior", "攒芝麻分进度", false));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 得乐园豆", false));
        //modelFields.addField(promise = new BooleanModelField("promise", "生活记录 | 坚持做", false));
        //modelFields.addField(promiseList = new SelectModelField("promiseList", "生活记录 | 坚持做列表", new LinkedHashSet<>(), PromiseSimpleTemplate::getList));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️蚂蚁会员:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initMember")) {
                initMemberTaskListMap(AutoAntMemberTaskList.getValue(), AutoMemberCreditSesameTaskList.getValue(), AntMemberTask.getValue(), collectSesame.getValue());
                Status.flagToday("BlackList::initMember");
            }
            
            if (memberSign.getValue()) {
                memberSign();
            }
            
            if (AntMemberTask.getValue()) {
                queryPointCert(1, 8);
                signPageTaskList();
                queryAllStatusTaskList();
            }
            
            memberPointExchangeBenefit();
            if (collectSesame.getValue()) {
                CheckInTaskRpcManager();
                collectSesame();
            }
            
            //芝麻积攒进度
            if (SesameGrowthBehavior.getValue()) {
                handleGrowthGuideTasks();
                queryAndCollect();
            }
            // 我的快递任务
            if (KuaiDiFuLiJia.getValue()) {
                RecommendTask();
                OrdinaryTask();
            }
            if (enableGameCenter.getValue()) {
                //检查并执行签到
                checkAndDoSignIn();
                //查询并处理任务列表
                queryAndProcessTaskList();
                
                //查询玩乐豆小球列表，有则领取
                queryPointBallList();
                
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void initMemberTaskListMap(boolean AutoAntMemberTaskList, boolean AutoMemberCreditSesameTaskList, boolean AntMemberTask, boolean collectSesame) {
        try {
            //初始化AntMemberTaskListMap
            AntMemberTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            //blackList.add("去淘金币逛一逛");
            // 可继续添加更多黑名单任务
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntMemberTaskListMap.add(task, task);
            }
            
            JSONObject jo;
            if (AntMemberTask) {
                boolean hasNextPage = true;
                int page = 1;
                do {
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.queryPointCert(page, 8));
                    TimeUtil.sleep(500);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        break;
                    }
                    hasNextPage = jo.optBoolean("hasNextPage");
                    page++;
                    JSONArray jaCertList = jo.optJSONArray("certList");
                    for (int i = 0; jaCertList != null && i < jaCertList.length(); i++) {
                        jo = jaCertList.optJSONObject(i);
                        if (jo == null) {
                            continue;
                        }
                        String bizTitle = jo.optString("bizTitle");
                        AntMemberTaskListMap.add(bizTitle, bizTitle);
                    }
                }
                while (hasNextPage);
                
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray availableTaskList = jo.optJSONArray("availableTaskList");
                    for (int i = 0; availableTaskList != null && i < availableTaskList.length(); i++) {
                        JSONObject task = availableTaskList.optJSONObject(i);
                        JSONObject taskConfigInfo = task != null ? task.optJSONObject("taskConfigInfo") : null;
                        if (taskConfigInfo == null) {
                            continue;
                        }
                        String name = taskConfigInfo.optString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                    JSONArray taskHistoryList = jo.optJSONArray("taskHistoryList");
                    for (int i = 0; taskHistoryList != null && i < taskHistoryList.length(); i++) {
                        JSONObject task = taskHistoryList.optJSONObject(i);
                        JSONObject taskConfigInfo = task != null ? task.optJSONObject("taskConfigInfo") : null;
                        if (taskConfigInfo == null) {
                            continue;
                        }
                        String name = taskConfigInfo.optString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                }
                
                //保存任务到配置文件
                AntMemberTaskListMap.save();
                Log.record("同步任务🉑会员任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntMemberTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField AntMemberTaskList = (SelectModelField) antMember.get("AntMemberTaskList");
                    if (AntMemberTaskList == null) {
                        return;
                    }
                    
                    Set<String> currentValues = AntMemberTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                AntMemberTaskList.add(task, 0);
                            }
                        }
                        
                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲会员任务自动设置: " + AntMemberTaskList.getValue());
                    }
                    else {
                        Log.record("会员任务黑白名单设置失败");
                    }
                }
            }
            //初始化MemberCreditSesameTaskListMap
            MemberCreditSesameTaskListMap.load();
            blackList = new HashSet<>();
            blackList.add("去淘金币逛一逛");
            blackList.add("坚持逛裹酱领福利");
            blackList.add("坚持签到领奖励");
            blackList.add("坚持看直播领福利");
            blackList.add("去雇佣芝麻大表鸽");
            blackList.add("完成旧衣回收得现金");
            blackList.add("0.1元起租会员攒粒");
            blackList.add("每日施肥领水果");
            blackList.add("去玩小游戏");
            // 可继续添加更多黑名单任务
            
            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛一逛芝麻树");
            whiteList.add("浏览15秒视频广告");
            whiteList.add("逛15秒商品橱窗");
            whiteList.add("逛一逛集汗滴找现金");
            whiteList.add("去体验先用后付");
            whiteList.add("去抛竿钓鱼");
            whiteList.add("去参与花呗活动");
            whiteList.add("坚持攒保障金");
            whiteList.add("去领支付宝积分");
            whiteList.add("去浏览租赁大促会场");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                MemberCreditSesameTaskListMap.add(task, task);
            }
            
            if (collectSesame) {
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONObject entrance = jo.optJSONObject("entrance");
                    if (entrance != null && entrance.optBoolean("openApp")) {
                        jo = MyUtils.newJSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            if (jo.has("data")) {
                                JSONObject data = jo.optJSONObject("data");
                                if (data != null && data.has("completeVOS")) {
                                    JSONArray completeVOS = data.optJSONArray("completeVOS");
                                    for (int i = 0; completeVOS != null && i < completeVOS.length(); i++) {
                                        JSONObject toCompleteVO = completeVOS.optJSONObject(i);
                                        if (toCompleteVO == null) {
                                            continue;
                                        }
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                                if (data != null && data.has("toCompleteVOS")) {
                                    JSONArray toCompleteVOS = data.optJSONArray("toCompleteVOS");
                                    for (int i = 0; toCompleteVOS != null && i < toCompleteVOS.length(); i++) {
                                        JSONObject toCompleteVO = toCompleteVOS.optJSONObject(i);
                                        if (toCompleteVO == null) {
                                            continue;
                                        }
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                            }
                        }
                    }
                }
                //保存任务到配置文件
                MemberCreditSesameTaskListMap.save();
                Log.record("同步任务🉑会员芝麻信用任务芝麻粒列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoMemberCreditSesameTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField MemberCreditSesameTaskList = (SelectModelField) antMember.get("MemberCreditSesameTaskList");
                    if (MemberCreditSesameTaskList == null) {
                        return;
                    }
                    
                    Set<String> currentValues = MemberCreditSesameTaskList.getValue();//该处直接返回列表地址
                    if (currentValues != null) {
                        for (String task : blackList) {
                            if (!currentValues.contains(task)) {
                                MemberCreditSesameTaskList.add(task, 0);
                            }
                        }
                        
                        // 3. 批量移除白名单任务（从现有列表中删除）
                        for (String task : whiteList) {
                            if (currentValues.contains(task)) {
                                currentValues.remove(task);
                            }
                        }
                    }
                    // 4. 保存配置
                    if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
                        Log.record("黑白名单🈲会员芝麻信用任务芝麻粒自动设置: " + MemberCreditSesameTaskList.getValue());
                    }
                    else {
                        Log.record("会员芝麻信用任务芝麻粒黑白名单设置失败");
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "initMemberTaskListMap err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.optBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.optString("signinSumDay") + "天]#获得[" + jo.optString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "memberSign err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryPointCert page=" + page + " 接口返回失败");
                return;
            }
            boolean hasNextPage = jo.optBoolean("hasNextPage");
            JSONArray jaCertList = jo.optJSONArray("certList");
            for (int i = 0; jaCertList != null && i < jaCertList.length(); i++) {
                jo = jaCertList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String bizTitle = jo.optString("bizTitle");
                //黑名单任务跳过
                if (AntMemberTaskList.getValue().contains(bizTitle)) {
                    continue;
                }
                String id = jo.optString("id");
                int pointAmount = jo.optInt("pointAmount");
                jo = MyUtils.newJSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPointCert err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 做任务赚积分
     */
    private void signPageTaskList() {
        try {
            do {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.signPageTaskList());
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    Log.i(TAG, "signPageTaskList 无 categoryTaskList 字段");
                    return;
                }
                JSONArray categoryTaskList = jo.optJSONArray("categoryTaskList");
                for (int i = 0; categoryTaskList != null && i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    JSONArray taskList = jo.optJSONArray("taskList");
                    String type = jo.optString("type");
                    if (taskList == null) {
                        continue;
                    }
                    if (Objects.equals("BROWSE", type)) {
                        doubleCheck = doBrowseTask(taskList);
                    }
                    else {
                        ExtensionsHandle.handleAlphaRequest("antMember", "doMoreTask", jo);
                    }
                }
                if (doubleCheck) {
                    continue;
                }
                break;
            }
            while (true);
        }
        catch (Throwable t) {
            Log.i(TAG, "signPageTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询所有状态任务列表
     */
    private void queryAllStatusTaskList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryAllStatusTaskList());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryAllStatusTaskList 接口返回失败");
                return;
            }
            JSONArray availableTaskList = jo.optJSONArray("availableTaskList");
            if (availableTaskList != null && doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryAllStatusTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 生活记录
    private void promise() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseQueryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            JSONArray promiseSimpleTemplates = jo != null ? jo.optJSONArray("promiseSimpleTemplates") : null;
            for (int i = 0; promiseSimpleTemplates != null && i < promiseSimpleTemplates.length(); i++) {
                jo = promiseSimpleTemplates.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String templateId = jo.optString("templateId");
                String promiseName = jo.optString("promiseName");
                String status = jo.optString("status");
                if ("un_join".equals(status) && promiseList.getValue().contains(templateId)) {
                    promiseJoin(querySingleTemplate(templateId));
                }
                PromiseSimpleTemplateIdMap.add(templateId, promiseName);
            }
            PromiseSimpleTemplateIdMap.save(UserIdMap.getCurrentUid());
        }
        catch (Throwable t) {
            Log.i(TAG, "promise err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private JSONObject querySingleTemplate(String templateId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.querySingleTemplate(templateId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            jo = jo.optJSONObject("data");
            if (jo == null) {
                return null;
            }
            JSONObject result = new JSONObject();

            result.put("joinFromOuter", false);
            result.put("templateId", jo.optString("templateId"));
            result.put("autoRenewStatus", Boolean.valueOf(jo.optString("autoRenewStatus")));

            JSONObject joinGuarantyRule = jo.optJSONObject("joinGuarantyRule");
            JSONArray joinGuarantyValues = joinGuarantyRule != null ? joinGuarantyRule.optJSONArray("canSelectValues") : null;
            if (joinGuarantyRule == null || joinGuarantyValues == null || joinGuarantyValues.length() == 0) {
                return null;
            }
            joinGuarantyRule.put("selectValue", joinGuarantyValues.optString(0));
            joinGuarantyRule.remove("canSelectValues");
            result.put("joinGuarantyRule", joinGuarantyRule);

            JSONObject joinRule = jo.optJSONObject("joinRule");
            JSONArray joinRuleValues = joinRule != null ? joinRule.optJSONArray("canSelectValues") : null;
            if (joinRule == null || joinRuleValues == null || joinRuleValues.length() == 0) {
                return null;
            }
            joinRule.put("selectValue", joinRuleValues.optString(0));
            joinRule.remove("joinRule");
            result.put("joinRule", joinRule);

            JSONObject periodTargetRule = jo.optJSONObject("periodTargetRule");
            JSONArray periodTargetValues = periodTargetRule != null ? periodTargetRule.optJSONArray("canSelectValues") : null;
            if (periodTargetRule == null || periodTargetValues == null || periodTargetValues.length() == 0) {
                return null;
            }
            periodTargetRule.put("selectValue", periodTargetValues.optString(0));
            periodTargetRule.remove("canSelectValues");
            result.put("periodTargetRule", periodTargetRule);

            JSONObject dataSourceRule = jo.optJSONObject("dataSourceRule");
            JSONArray dataSourceValues = dataSourceRule != null ? dataSourceRule.optJSONArray("canSelectValues") : null;
            JSONObject firstDataSource = dataSourceValues != null ? dataSourceValues.optJSONObject(0) : null;
            if (dataSourceRule == null || firstDataSource == null) {
                return null;
            }
            dataSourceRule.put("selectValue", firstDataSource.optString("merchantId"));
            dataSourceRule.remove("canSelectValues");
            result.put("dataSourceRule", dataSourceRule);
            return result;
        }
        catch (Throwable t) {
            Log.i(TAG, "querySingleTemplate err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }
    
    private void promiseJoin(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseJoin(data));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            String promiseName = jo != null ? jo.optString("promiseName") : "";
            Log.other("生活记录📝加入[" + promiseName + "]");
        }
        catch (Throwable t) {
            Log.i(TAG, "promiseJoin err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    // 查询持续做明细任务
    private JSONObject promiseQueryDetail(String recordId) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseQueryDetail(recordId));
        if (!jo.optBoolean("success")) {
            return null;
        }
        return jo;
    }
    
    // 蚂蚁积分-做浏览任务
    private Boolean doBrowseTask(JSONArray taskList) {
        boolean doubleCheck = false;
        try {
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                if (task.optBoolean("hybrid")) {
                    JSONObject extInfo = task.optJSONObject("extInfo");
                    if (extInfo == null) {
                        continue;
                    }
                    int periodCurrentCount = Integer.parseInt(extInfo.optString("PERIOD_CURRENT_COUNT", "0"));
                    int periodTargetCount = Integer.parseInt(extInfo.optString("PERIOD_TARGET_COUNT", "0"));
                    int count = periodTargetCount > periodCurrentCount ? periodTargetCount - periodCurrentCount : 0;
                    if (count > 0) {
                        doubleCheck = doubleCheck || doBrowseTask(task, periodTargetCount, periodTargetCount);
                    }
                }
                else {
                    doubleCheck = doubleCheck || doBrowseTask(task, 1, 1);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }
    
    private Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.optJSONObject("taskConfigInfo");
            if (taskConfigInfo == null) {
                return false;
            }
            String name = taskConfigInfo.optString("name");
            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(name)) {
                return false;
            }
            Long id = taskConfigInfo.optLong("id");
            JSONObject awardParam = taskConfigInfo.optJSONObject("awardParam");
            JSONArray targetBusinessArr = taskConfigInfo.optJSONArray("targetBusiness");
            if (awardParam == null || targetBusinessArr == null || targetBusinessArr.length() == 0) {
                return false;
            }
            String awardParamPoint = awardParam.optString("awardParamPoint");
            String targetBusiness = targetBusinessArr.optString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.applyTask(name, id));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String[] targetBusinessArray = targetBusiness.split("#");
                String bizParam;
                String bizSubType;
                if (targetBusinessArray.length > 2) {
                    bizParam = targetBusinessArray[2];
                    bizSubType = targetBusinessArray[1];
                }
                else {
                    bizParam = targetBusinessArray[1];
                    bizSubType = targetBusinessArray[0];
                }
                jo = MyUtils.newJSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    continue;
                }
                String ex = left == right && left == 1 ? "" : "(" + (i + 1) + "/" + right + ")";
                Log.other("会员任务🎖️完成[" + name + ex + "]#获得[" + awardParamPoint + "积分]");
                doubleCheck = true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doBrowseTask err:");
            Log.printStackTrace(TAG, t);
        }
        return doubleCheck;
    }
    

    
    /**
     * 芝麻分任务处理（每日问答、公益任务、芭芭农场施肥等）
     */
    private void handleGrowthGuideTasks() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject root = MyUtils.newJSONObject(AntMemberRpcCall.queryGrowthBehaviorToDoList());
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return;
            }
            
            // 待处理任务列表
            JSONArray toDoList = root.optJSONArray("toDoList");
            int toDoCount = toDoList == null ? 0 : toDoList.length();
            if (toDoList == null || toDoCount == 0) {
                return;
            }
            
            for (int i = 0; i < toDoList.length(); i++) {
                JSONObject task = toDoList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                
                String behaviorId = task.optString("behaviorId", "");
                String title = task.optString("title", "");
                String status = task.optString("status", "");
                String subTitle = task.optString("subTitle", "");
                
                // 公益类任务（待领取）
                if ("wait_receive".equals(status)) {
                    String openResp = AntMemberRpcCall.openBehaviorCollect(behaviorId);
                    JSONObject openJo = MyUtils.newJSONObject(openResp);
                    if (MessageUtil.checkResultCode(TAG, openJo)) {
                        Log.other("攒芝麻分🧾任务领取：" + title);
                    }
                    continue;
                }
                
                // 每日问答
                if ("meiriwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    if (subTitle.contains("今日已参与")) {
                        Log.other("攒芝麻分🧾[每日问答] " + subTitle + "（跳过答题）");
                        continue;
                    }
                    
                    // 查询题目
                    JSONObject quizJo = MyUtils.newJSONObject(AntMemberRpcCall.queryDailyQuiz(behaviorId));
                    if (!MessageUtil.checkSuccess(TAG, quizJo)) {
                        continue;
                    }
                    JSONObject data = quizJo.optJSONObject("data");
                    if (data == null) {
                        continue;
                    }
                    
                    JSONObject qVo = data.optJSONObject("questionVo");
                    if (qVo == null) {
                        continue;
                    }
                    
                    JSONObject rightAnswer = qVo.optJSONObject("rightAnswer");
                    if (rightAnswer == null) {
                        continue;
                    }
                    
                    long bizDate = data.optLong("bizDate", 0L);
                    String questionId = qVo.optString("questionId", "");
                    String questionContent = qVo.optString("questionContent", "");
                    String answerId = rightAnswer.optString("answerId", "");
                    String answerContent = rightAnswer.optString("answerContent", "");
                    
                    if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                        continue;
                    }
                    
                    // 提交答案
                    JSONObject pushJo = MyUtils.newJSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, "RIGHT"));
                    if (MessageUtil.checkResultCode(TAG, pushJo)) {
                        Log.other("攒芝麻分🎖️[每日答题成功] " + questionContent + " | 答案=" + answerContent + "(" + answerId + ")" + (subTitle.isEmpty() ? "" : " | " + subTitle));
                    }
                }
                
                // 视频问答
                if ("shipingwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    long bizDate = System.currentTimeMillis();
                    String questionId = "question3";
                    String answerId = "A";
                    String answerType = "RIGHT";
                    
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, answerType));
                    
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("攒芝麻分🎖️[视频问答提交成功]");
                    }
                }
                
                // 芭芭农场施肥
                if ("babanongchang_7d".equals(behaviorId) && "wait_doing".equals(status)) {
                    
                    // 获取WUA
                    String wua = new AntOrchard().getWua();
                    String source = "DNHZ_NC_zhimajingnangSF";
                    
                    JSONObject spreadManureData = MyUtils.newJSONObject(AntOrchardRpcCall.orchardSpreadManure(false, wua));
                    
                    if (!"100".equals(spreadManureData.optString("resultCode"))) {
                        continue;
                    }
                    
                    String taobaoDataStr = spreadManureData.optString("taobaoData", "");
                    if (taobaoDataStr.isEmpty()) {
                        continue;
                    }
                    
                    JSONObject spreadTaobaoData = MyUtils.newJSONObject(taobaoDataStr);
                    
                    JSONObject currentStage = spreadTaobaoData.optJSONObject("currentStage");
                    if (currentStage == null) {
                        Log.error(TAG + "GrowthGuideTasks" + "芭芭农场[缺少currentStage]");
                        continue;
                    }
                    
                    String stageText = currentStage.optString("stageText", "");
                    JSONObject statistics = spreadTaobaoData.optJSONObject("statistics");
                    int dailyAppWateringCount = statistics == null ? 0 : statistics.optInt("dailyAppWateringCount", 0);
                    
                    Log.farm("芭芭农场🌳施肥" + dailyAppWateringCount + "次[" + stageText + "]");
                    Log.other("攒芝麻分🎖️芭芭农场施肥[" + title + "]已施肥" + dailyAppWateringCount + "次");
                    
                }
            }
        }
        catch (Throwable e) {
            Log.printStackTrace(TAG + ".handleGrowthGuideTasks", e);
        }
    }

    
    public static void queryAndCollect() {
        try {
            // 1. 查询进度球状态
            String queryResp = AntMemberRpcCall.queryScoreProgress();
            if (queryResp == null || queryResp.isEmpty()) {
                return;
            }
            
            JSONObject json = MyUtils.newJSONObject(queryResp);
            
            // 检查 success
            if (!MessageUtil.checkSuccess(TAG, json)) {
                return;
            }
            
            JSONObject totalWait = json.optJSONObject("totalWaitProcessVO");
            if (totalWait == null) {
                return;
            }
            
            JSONArray idList = totalWait.optJSONArray("totalProgressIdList");
            if (idList == null || idList.length() == 0) {
                return;
            }
            
            // 直接传 JSONArray
            String collectResp = AntMemberRpcCall.collectProgressBall(idList);
            if (collectResp == null) {
                return;
            }
            
            JSONObject collectJson = MyUtils.newJSONObject(collectResp);
            int collectedAccelerateProgress = collectJson.optInt("collectedAccelerateProgress", -1);
            int currentAccelerateValue = collectJson.optInt("currentAccelerateValue", 0);
            int totalAccelerateProgress = collectJson.optInt("totalAccelerateProgress", 0);
            Log.other("攒芝麻分🎁领取#本次加速进度:" + collectedAccelerateProgress + "(总" + totalAccelerateProgress + "%)加速倍率:" + currentAccelerateValue);
        }
        catch (Exception e) {
            Log.printStackTrace(TAG + "queryAndCollect err", e);
        }
    }
    
    //游戏中心任务
    
    /**
     * 批量领取玩乐豆
     */
    public static void batchReceivePointBall() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.batchReceivePointBall());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.optJSONObject("data");
                String totalAmount = dataObj != null ? dataObj.optString("totalAmount") : "";
                Log.other("游戏中心🎮批量领取#获得[" + totalAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "batchReceivePointBall err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 每日签到
     *
     * @return 签到是否成功
     */
    public static boolean dailySignIn() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.continueSignIn());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject data = jsonObject.optJSONObject("data");
                JSONObject toastModule = data != null ? data.optJSONObject("autoSignInToastModule") : null;
                if (toastModule == null) {
                    return false;
                }
                String desc = toastModule.optString("desc");
                String beanNum = desc.substring(desc.indexOf("玩乐豆+") + 4);
                Log.other("游戏中心🎮每日签到#获得[" + beanNum + "玩乐豆]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "continueSignIn err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    /**
     * 处理单个任务
     *
     * @param taskObj 任务JSON对象
     */
    public static void processTask(JSONObject taskObj) {
        try {
            if (!"VIEW".equals(taskObj.optString("actionType"))) {
                return;
            }

            String taskId = taskObj.optString("taskId");
            String subTitle = taskObj.optString("subTitle");
            String taskStatus = taskObj.optString("taskStatus");
            int prizeAmount = taskObj.optInt("prizeAmount");

            // 任务未完成且需要报名
            if ("NOT_DONE".equals(taskStatus) && taskObj.optBoolean("needSignUp")) {
                JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.doTaskSignup(taskId));
                if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                    return;
                }
            }
            
            // 执行任务
            JSONObject doTaskjo = MyUtils.newJSONObject(AntMemberRpcCall.doTaskSend(taskId));
            if (MessageUtil.checkSuccess(TAG, doTaskjo)) {
                Log.other("游戏中心🎮完成任务[" + subTitle + "]#待领[" + prizeAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "doTask err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询并处理任务列表
     */
    public static void queryAndProcessTaskList() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryModularTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONObject data = jsonObject.optJSONObject("data");
            JSONArray taskModuleList = data != null ? data.optJSONArray("taskModuleList") : null;
            for (int i = 0; taskModuleList != null && i < taskModuleList.length(); i++) {
                JSONObject moduleObj = taskModuleList.optJSONObject(i);
                JSONArray taskList = moduleObj != null ? moduleObj.optJSONArray("taskList") : null;
                for (int j = 0; taskList != null && j < taskList.length(); j++) {
                    JSONObject taskItem = taskList.optJSONObject(j);
                    if (taskItem != null) {
                        processTask(taskItem);
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryModularTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void queryTaskList() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONObject data = jsonObject.optJSONObject("data");
            JSONObject gameTaskModule = data != null ? data.optJSONObject("gameTaskModule") : null;
            JSONArray gameTaskList = gameTaskModule != null ? gameTaskModule.optJSONArray("gameTaskList") : null;
            for (int i = 0; gameTaskList != null && i < gameTaskList.length(); i++) {
                JSONObject taskItem = gameTaskList.optJSONObject(i);
                if (taskItem != null) {
                    processTask(taskItem);
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryModularTaskList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 查询玩乐豆小球列表，有则领取
     */
    public static void queryPointBallList() {
        try {
            String response = ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{}]");
            JSONObject jsonObject = MyUtils.newJSONObject(response);
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject data = jsonObject.optJSONObject("data");
                JSONArray pointBallList = data != null ? data.optJSONArray("pointBallList") : null;
                if (pointBallList != null && pointBallList.length() > 0) {
                    batchReceivePointBall();
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "queryPointBallList err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 检查并执行签到
     */
    public static void checkAndDoSignIn() {
        if (Status.hasFlagToday("gameCenterSignIn")) {
            return;
        }
        
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryPointBallList());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.optJSONObject("data");
                if (dataObj != null && dataObj.has("signInBallModule")) {
                    JSONObject signInModule = dataObj.optJSONObject("signInBallModule");
                    if (signInModule != null && !signInModule.optBoolean("signInStatus")) {
                        if (dailySignIn()) {
                            Status.flagToday("gameCenterSignIn");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "querySignInBall err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    /*
    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.querySignInBall", jsonObject.optString("resultDesc"));
                    return;
                }
                str = JsonUtil.getValueByPath(jsonObject, "data.signInBallModule.signInStatus");
                if (String.valueOf(true).equals(str)) {
                    return;
                }
                str = AntMemberRpcCall.continueSignIn();
                TimeUtil.sleep(300);
                jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.record("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.i(TAG, "signIn err:");
                Log.printStackTrace(TAG, th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".batchReceive.queryPointBallList", jsonObject.optString("resultDesc"));
                    return;
                }
                JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "data.pointBallList");
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                str = AntMemberRpcCall.batchReceivePointBall();
                TimeUtil.sleep(300);
                jsonObject = MyUtils.newJSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.i(TAG, "batchReceive err:");
                Log.printStackTrace(TAG, th);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    */
    // 会员积分兑换 - 获取权益列表（无条件）+ 兑换（受开关控制）
    private void memberPointExchangeBenefit() {
        try {
            String userId = UserIdMap.getCurrentUid();
            // 依次尝试多个 deliveryId，找到可用的分类
            String[] deliveryIds = {"94000SR2023102305988003", "94000SR2024011106752003", "94000SR2024071108523003", "94000SR2024071808609003"};
            JSONObject jo = null;
            for (String deliveryId : deliveryIds) {
                JSONObject candidate = MyUtils.newJSONObject(AntMemberRpcCall.queryDeliveryZoneDetail(userId, deliveryId));
                JSONArray candidateList = candidate.optJSONArray("entityInfoList");
                if (MessageUtil.checkResultCode(TAG, candidate) && candidateList != null && candidateList.length() > 0) {
                    Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 成功，权益数=" + candidateList.length());
                    jo = candidate;
                    break;
                }
                Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 失败，尝试下一个");
            }
            if (jo == null) {
                // 所有 deliveryId 都失败，尝试备用接口
                Log.i(TAG, "queryDeliveryZoneDetail 全部失败，尝试备用接口");
                fetchBenefitsFromNavi(userId);
                MemberBenefitIdMap.save(userId);
                return;
            }
            JSONArray entityInfoList = jo.optJSONArray("entityInfoList");
            if (entityInfoList == null || entityInfoList.length() == 0) {
                Log.record("会员积分[当前分类无可兑换权益，尝试备用接口]");
                fetchBenefitsFromNavi(userId);
                MemberBenefitIdMap.save(userId);
                return;
            }
            // 无条件保存权益列表（供用户勾选），兑换与否受开关和勾选控制
            java.util.Set<String> selectedIds = memberPointExchangeBenefitList.getValue();
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.optJSONObject(i);
                JSONObject benefitInfo = entityInfo != null ? entityInfo.optJSONObject("benefitInfo") : null;
                JSONObject pricePresentation = benefitInfo != null ? benefitInfo.optJSONObject("pricePresentation") : null;
                if (benefitInfo == null || pricePresentation == null || !"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.optString("name");
                String benefitId = benefitInfo.optString("benefitId");
                MemberBenefitIdMap.add(benefitId, name);
            }
            MemberBenefitIdMap.save(userId);

            // 开关关闭则不兑换
            if (!memberPointExchangeBenefit.getValue()) {
                Log.i(TAG, "会员积分兑换开关已关闭，仅更新权益列表");
                return;
            }
            if (selectedIds.isEmpty()) {
                Log.i(TAG, "会员积分兑换已开启，请在列表中选择要兑换的权益");
            }
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.optJSONObject(i);
                JSONObject benefitInfo = entityInfo != null ? entityInfo.optJSONObject("benefitInfo") : null;
                JSONObject pricePresentation = benefitInfo != null ? benefitInfo.optJSONObject("pricePresentation") : null;
                if (benefitInfo == null || pricePresentation == null || !"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.optString("name");
                String benefitId = benefitInfo.optString("benefitId");
                // 只兑换用户在列表中勾选的权益
                if (!selectedIds.contains(benefitId)) {
                    continue;
                }
                if (!Status.canMemberPointExchangeBenefitToday(benefitId)) {
                    continue;
                }
                String itemId = benefitInfo.optString("itemId");
                if (exchangeBenefit(benefitId, itemId)) {
                    String point = pricePresentation.optString("point");
                    Log.other("会员积分🎐兑换[" + name + "]#花费[" + point + "积分]");
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "memberPointExchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
    }

    // 备用接口：通过导航分类码查询权益列表（支持分页）
    private void fetchBenefitsFromNavi(String userId) {
        try {
            // 依次尝试各导航分类码：特色(14)、出行(1)、美食(11)、日用(12)
            String[] naviCodes = {"14", "1", "11", "12", "13", "bb82b", ""};
            for (String naviCode : naviCodes) {
                int pageNum = 1;
                int totalPages = 1;
                Log.i(TAG, "fetchBenefitsFromNavi start naviCode=" + naviCode);
                do {
                    String raw = AntMemberRpcCall.queryIndexNaviBenefitFlowV2(userId, naviCode, pageNum);
                    JSONObject jo = MyUtils.newJSONObject(raw);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " resultCode=" + jo.optString("resultCode") + " desc=" + jo.optString("desc"));
                        break;
                    }
                    JSONArray benefitList = jo.optJSONArray("entityInfoList");
                    if (benefitList == null || benefitList.length() == 0) {
                        JSONObject data = jo.optJSONObject("data");
                        benefitList = data != null ? data.optJSONArray("entityInfoList") : null;
                    }
                    if (benefitList == null || benefitList.length() == 0) {
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " 无权益数据，raw=" + raw.substring(0, Math.min(300, raw.length())));
                        break;
                    }
                    // 获取总页数
                    if (pageNum == 1) {
                        int next = jo.optInt("nextPageNum", 0);
                        int adNext = jo.optInt("nextAdPageNum", 0);
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " nextPageNum=" + next + " nextAdPageNum=" + adNext);
                        if (next > 1) {
                            totalPages = next;
                        } else if (adNext > 1) {
                            totalPages = adNext;
                        }
                    }
                    // 从 entityInfoList 解析完整权益信息
                    int saved = 0, skipped = 0;
                    for (int i = 0; i < benefitList.length(); i++) {
                        JSONObject entity = benefitList.optJSONObject(i);
                        JSONObject benefitInfo = entity != null ? entity.optJSONObject("benefitInfo") : null;
                        if (benefitInfo == null) { skipped++; continue; }
                        JSONObject pricePresentation = benefitInfo.optJSONObject("pricePresentation");
                        if (pricePresentation == null) { skipped++; continue; }
                        String strategyType = pricePresentation.optString("strategyType");
                        if (!"POINT_PAY".equals(strategyType)) {
                            Log.i(TAG, "  跳过[" + benefitInfo.optString("name") + "] strategyType=" + strategyType);
                            skipped++;
                            continue;
                        }
                        String name = benefitInfo.optString("name");
                        String benefitId = benefitInfo.optString("benefitId");
                        if (benefitId.isEmpty()) { skipped++; continue; }
                        Log.i(TAG, "  保存[" + name + "] benefitId=" + benefitId);
                        MemberBenefitIdMap.add(benefitId, name);
                        saved++;
                    }
                    // 从 extInfo 补充 entityInfoList 中缺失的 benefitId
                    JSONObject extInfo = jo.optJSONObject("extInfo");
                    if (extInfo != null && extInfo.length() > 0) {
                        int extAdded = 0;
                        for (Iterator<String> it = extInfo.keys(); it.hasNext(); ) {
                            String key = it.next();
                            // 跳过非 benefitId 的元数据 key
                            if ("promoSceneCode".equals(key) || key.startsWith("AMS") == false && key.length() < 10) {
                                continue;
                            }
                            if (!MemberBenefitIdMap.getMap().containsKey(key)) {
                                Log.i(TAG, "  补充[extInfo] benefitId=" + key);
                                MemberBenefitIdMap.add(key, "会员积分权益");
                                extAdded++;
                            }
                        }
                        if (extAdded > 0) {
                            Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " extInfo补充" + extAdded + "个");
                        }
                    }
                    Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " 保存" + saved + "个，补充" + (extInfo != null ? extInfo.length() : 0) + "个，累计" + MemberBenefitIdMap.getMap().size());
                    pageNum++;
                } while (pageNum <= totalPages);
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "fetchBenefitsFromNavi err:");
            Log.printStackTrace(TAG, t);
        }
    }
    
    private Boolean exchangeBenefit(String benefitId, String itemId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.exchangeBenefit(benefitId, itemId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                return true;
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "exchangeBenefit err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }
    
    private void collectSesame() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.optJSONObject("entrance");
            if (entrance == null || !entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }

            jo = MyUtils.newJSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.optJSONArray("toCompleteVOS");
            for (int i = 0; toCompleteVOS != null && i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.optJSONObject(i);
                if (toCompleteVO == null) {
                    continue;
                }
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.optString("title") : "未知任务";
                //黑名单任务跳过
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }
                
                boolean finishFlag = toCompleteVO.optBoolean("finishFlag", false);
                String actionText = toCompleteVO.optString("actionText", "");
                
                // 检查任务是否已完成
                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }
                
                if (!toCompleteVO.has("templateId")) {
                    continue;
                }
                
                String taskTemplateId = toCompleteVO.optString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.optInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                String s = null;
                String recordId = null;
                JSONObject responseObj = null;
                
                if (!toCompleteVO.has("todayFinish")) {
                    // 领取任务
                    s = AntMemberRpcCall.joinSesameTask(taskTemplateId);
                    responseObj = MyUtils.newJSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    TimeUtil.sleep(200);
                    if (!MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.error(TAG + "芝麻信用💳领取任务[" + taskTitle + "]失败#" + s);
                        continue;
                    }
                    JSONObject responseData = responseObj.optJSONObject("data");
                    recordId = responseData != null ? responseData.optString("recordId") : null;
                    if (recordId == null) {
                        continue;
                    }
                }
                else {
                    if (!toCompleteVO.has("recordId")) {
                        Log.error(TAG + "芝麻信用💳任务[" + taskTitle + "未获取到]recordId#" + toCompleteVO);
                        continue;
                    }
                    recordId = toCompleteVO.optString("recordId");
                }
                
                // 完成任务
                for (int j = completedNum; j < needCompleteNum; j++) {
                    s = AntMemberRpcCall.finishSesameTask(recordId);
                    TimeUtil.sleep(2000);
                    responseObj = MyUtils.newJSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    
                    if (MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.record("芝麻信用💳完成任务[" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)");
                    }
                    else {
                        Log.error("芝麻信用💳完成任务[" + taskTitle + "]失败#" + s);
                    }
                }
                
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.optJSONArray("creditFeedbackVOS");
                for (int j = 0; ja != null && j < ja.length(); j++) {
                    jo = ja.optJSONObject(j);
                    if (jo == null || !"UNCLAIMED".equals(jo.optString("status"))) {
                        continue;
                    }
                    //String title = jo.getString("title");
                    String creditFeedbackId = jo.optString("creditFeedbackId");
                    String potentialSize = jo.optString("potentialSize");
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }
            jo = MyUtils.newJSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.optJSONArray("creditFeedbackVOS");
            if (creditFeedbackVOS != null && creditFeedbackVOS.length() != 0) {
                jo = MyUtils.newJSONObject(AntMemberRpcCall.collectAllCreditFeedback());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    String resultCode = jo.optString("resultCode");
                    Log.other("收芝麻粒🙇🏻‍♂️[一键收取]" + resultCode);
                }
            }
            
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    private void CheckInTaskRpcManager() {
        if (Status.hasFlagToday("AntMember::zmlCheckIn")) {
            return;
        }
        try {
            
            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = MyUtils.newJSONObject(checkInRes);
            if (MessageUtil.checkResultCode(TAG, checkInJo)) {
                JSONObject data = checkInJo.optJSONObject("data");
                if (data != null) {
                    JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
                    if (currentDay != null) {
                        String status = currentDay.optString("status");
                        String checkInDate = currentDay.optString("checkInDate");
                        if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                            String completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml");
                            try {
                                JSONObject completeJo = MyUtils.newJSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            Status.flagToday("AntMember::zmlCheckIn");
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameZmlCheckIn", t);
        }
    }
    
    // 我的快递任务
    private void RecommendTask() {
        try {
            // 调用 AntMemberRpcCall.queryRecommendTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryRecommendTask();
            JSONObject jsonResponse = MyUtils.newJSONObject(response);
            // 获取 taskDetailList 数组
            JSONArray taskDetailList = jsonResponse.optJSONArray("taskDetailList");
            // 遍历 taskDetailList
            for (int i = 0; taskDetailList != null && i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.optJSONObject(i);
                if (taskDetail == null) {
                    continue;
                }
                // 检查 "canAccess" 的值是否为 true
                boolean canAccess = taskDetail.optBoolean("canAccess", false);
                if (!canAccess) {
                    // 如果 "canAccess" 不为 true，跳过
                    continue;
                }
                // 获取 taskMaterial 对象
                JSONObject taskMaterial = taskDetail.optJSONObject("taskMaterial");
                // 获取 taskBaseInfo 对象
                JSONObject taskBaseInfo = taskDetail.optJSONObject("taskBaseInfo");
                if (taskMaterial == null) {
                    continue;
                }
                // 获取 taskCode
                String taskCode = taskMaterial.optString("taskCode", "");
                // 根据 taskCode 执行不同的操作
                if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode) || "WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                    if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode)) {
                        //String forestHomePageResponse = AntMemberRpcCall.queryforestHomePage();
                        //TimeUtil.sleep(2000);
                        String forestTaskResponse = AntMemberRpcCall.forestTask();
                        TimeUtil.sleep(500);
                        String forestreceiveTaskAward = AntMemberRpcCall.forestreceiveTaskAward();
                    }
                    else if ("WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                        //String oceanHomePageResponse = AntMemberRpcCall.queryoceanHomePage();
                        //TimeUtil.sleep(2000);
                        String oceanTaskResponse = AntMemberRpcCall.oceanTask();
                        TimeUtil.sleep(500);
                        String oceanreceiveTaskAward = AntMemberRpcCall.oceanreceiveTaskAward();
                    }
                    if (taskBaseInfo != null) {
                        String appletName = taskBaseInfo.optString("appletName", "Unknown Applet");
                        Log.other("我的快递💌完成[" + appletName + "]");
                    }
                }
                if (taskMaterial == null || !taskMaterial.has("taskId")) {
                    // 如果 taskMaterial 为 null 或者不包含 taskId，跳过
                    continue;
                }
                // 获取 taskId
                String taskId = taskMaterial.optString("taskId");
                // 调用 trigger 方法
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = MyUtils.newJSONObject(triggerResponse);
                // 检查 success 字段
                boolean success = triggerResult.optBoolean("success");
                if (success) {
                    // 从 triggerResponse 中获取 prizeSendInfo 数组
                    JSONArray prizeSendInfo = triggerResult.optJSONArray("prizeSendInfo");
                    JSONObject prizeInfo = prizeSendInfo != null && prizeSendInfo.length() > 0 ? prizeSendInfo.optJSONObject(0) : null;
                    JSONObject extInfo = prizeInfo != null ? prizeInfo.optJSONObject("extInfo") : null;
                    if (extInfo != null) {
                        // 获取 promoCampName
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        // 输出日志信息
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "RecommendTask err:");
            Log.printStackTrace(TAG, th);
        }
    }
    
    private void OrdinaryTask() {
        try {
            // 调用 AntMemberRpcCall.queryOrdinaryTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = MyUtils.newJSONObject(response);
            // 检查是否请求成功
            if (jsonResponse.optBoolean("success")) {
                // 获取任务详细列表
                JSONArray taskDetailList = jsonResponse.optJSONArray("taskDetailList");
                // 遍历任务详细列表
                for (int i = 0; taskDetailList != null && i < taskDetailList.length(); i++) {
                    // 获取当前任务对象
                    JSONObject task = taskDetailList.optJSONObject(i);
                    if (task == null) {
                        continue;
                    }
                    // 提取任务 ID、处理状态和触发类型
                    String taskId = task.optString("taskId");
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String sendCampTriggerType = task.optString("sendCampTriggerType");
                    // 检查任务状态和触发类型，执行触发操作
                    if (!"RECEIVE_SUCCESS".equals(taskProcessStatus) && !"EVENT_TRIGGER".equals(sendCampTriggerType)) {
                        // 调用 signuptrigger 方法
                        String signuptriggerResponse = AntMemberRpcCall.signuptrigger(taskId);
                        // 调用 sendtrigger 方法
                        String sendtriggerResponse = AntMemberRpcCall.sendtrigger(taskId);
                        // 解析 sendtriggerResponse
                        JSONObject sendTriggerJson = MyUtils.newJSONObject(sendtriggerResponse);
                        // 判断任务是否成功
                        if (sendTriggerJson.optBoolean("success")) {
                            // 从 sendtriggerResponse 中获取 prizeSendInfo 数组
                            JSONArray prizeSendInfo = sendTriggerJson.optJSONArray("prizeSendInfo");
                            JSONObject firstPrize = prizeSendInfo != null && prizeSendInfo.length() > 0 ? prizeSendInfo.optJSONObject(0) : null;
                            if (firstPrize != null) {
                                // 获取 prizeName
                                String prizeName = firstPrize.optString("prizeName");
                                Log.other("我的快递💌完成[" + prizeName + "]");
                            }
                        }
                        else {
                            Log.i(TAG, "sendtrigger failed for taskId: " + taskId);
                        }
                        TimeUtil.sleep(1000);
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.i(TAG, "OrdinaryTask err:");
            Log.printStackTrace(TAG, th);
        }
    }
}
