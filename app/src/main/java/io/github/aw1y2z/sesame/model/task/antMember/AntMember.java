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
    private BooleanModelField enableGoldTicket;
    private BooleanModelField KuaiDiFuLiJia;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AntMemberTask = new BooleanModelField("AntMemberTask", "会员任务", false));
        modelFields.addField(AutoAntMemberTaskList = new BooleanModelField("AutoAntMemberTaskList", "会员任务 | 自动黑名单", true).setDependsOn("AntMemberTask"));
        modelFields.addField(AntMemberTaskList = new SelectModelField("AntMemberTaskList", "会员任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntMemberTaskList::getList).setDependsOn("AutoAntMemberTaskList"));
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList).setDependsOn("memberPointExchangeBenefit"));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 领取", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒任务 | 自动黑名单", true).setDependsOn("collectSesame"));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒任务 | 黑名单列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList).setDependsOn("AutoMemberCreditSesameTaskList"));
        modelFields.addField(SesameGrowthBehavior = new BooleanModelField("SesameGrowthBehavior", "攒芝麻分进度", false));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 得乐园豆", false));
        //modelFields.addField(promise = new BooleanModelField("promise", "生活记录 | 坚持做", false));
        //modelFields.addField(promiseList = new SelectModelField("promiseList", "生活记录 | 坚持做列表", new LinkedHashSet<>(), PromiseSimpleTemplate::getList));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        modelFields.addField(enableGoldTicket = new BooleanModelField("enableGoldTicket", "黄金票 | 签到", false));
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
            if (enableGoldTicket.getValue()) {
                goldTicket();
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
                    jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, 8));
                    TimeUtil.sleep(500);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        break;
                    }
                    hasNextPage = jo.getBoolean("hasNextPage");
                    page++;
                    JSONArray jaCertList = jo.getJSONArray("certList");
                    for (int i = 0; i < jaCertList.length(); i++) {
                        jo = jaCertList.getJSONObject(i);
                        String bizTitle = jo.getString("bizTitle");
                        AntMemberTaskListMap.add(bizTitle, bizTitle);
                    }
                }
                while (hasNextPage);
                
                jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
                    for (int i = 0; i < availableTaskList.length(); i++) {
                        JSONObject task = availableTaskList.getJSONObject(i);
                        JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
                        String name = taskConfigInfo.getString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                    JSONArray taskHistoryList = jo.getJSONArray("taskHistoryList");
                    for (int i = 0; i < taskHistoryList.length(); i++) {
                        JSONObject task = taskHistoryList.getJSONObject(i);
                        JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
                        String name = taskConfigInfo.getString("name");
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
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员任务", blackList, whiteList, AntMemberTaskList);
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
            // 注："去玩小游戏" 不再预置拉黑，交由自动拉黑机制判定
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
                jo = new JSONObject(AntMemberRpcCall.queryHome());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONObject entrance = jo.getJSONObject("entrance");
                    if (entrance.optBoolean("openApp")) {
                        jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            if (jo.has("data")) {
                                JSONObject data = jo.getJSONObject("data");
                                if (data.has("completeVOS")) {
                                    JSONArray completeVOS = data.getJSONArray("completeVOS");
                                    for (int i = 0; i < completeVOS.length(); i++) {
                                        JSONObject toCompleteVO = completeVOS.getJSONObject(i);
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                                if (data.has("toCompleteVOS")) {
                                    JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
                                    for (int i = 0; i < toCompleteVOS.length(); i++) {
                                        JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
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
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员芝麻信用任务芝麻粒", blackList, whiteList, MemberCreditSesameTaskList);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "initMemberTaskListMap err:", t);
        }
    }
    
    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.getBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.getString("signinSumDay") + "天]#获得[" + jo.getString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "memberSign err:", t);
        }
    }
    
    private void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryPointCert page=" + page + " 接口返回失败");
                return;
            }
            boolean hasNextPage = jo.getBoolean("hasNextPage");
            JSONArray jaCertList = jo.getJSONArray("certList");
            Log.i(TAG, "queryPointCert page=" + page + " certList.size=" + jaCertList.length() + " hasNextPage=" + hasNextPage);
            for (int i = 0; i < jaCertList.length(); i++) {
                jo = jaCertList.getJSONObject(i);
                String bizTitle = jo.getString("bizTitle");
                //黑名单任务跳过
                if (AntMemberTaskList.getValue().contains(bizTitle)) {
                    continue;
                }
                String id = jo.getString("id");
                int pointAmount = jo.getInt("pointAmount");
                jo = new JSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointCert err:", t);
        }
    }
    
    /**
     * 做任务赚积分
     */
    private void signPageTaskList() {
        try {
            do {
                String rawJson = AntMemberRpcCall.signPageTaskList();
                JSONObject jo = new JSONObject(rawJson);
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    Log.i(TAG, "signPageTaskList 无 categoryTaskList 字段");
                    return;
                }
                JSONArray categoryTaskList = jo.getJSONArray("categoryTaskList");
                for (int i = 0; i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.getJSONObject(i);
                    JSONArray taskList = jo.getJSONArray("taskList");
                    String type = jo.getString("type");
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
            Log.err(TAG, "signPageTaskList err:", t);
        }
    }
    
    /**
     * 查询所有状态任务列表
     */
    private void queryAllStatusTaskList() {
        try {
            String rawJson = AntMemberRpcCall.queryAllStatusTaskList();
            JSONObject jo = new JSONObject(rawJson);
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryAllStatusTaskList 接口返回失败");
                return;
            }
            JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
            if (doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryAllStatusTaskList err:", t);
        }
    }
    
    // 生活记录
    private void promise() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray promiseSimpleTemplates = jo.getJSONArray("promiseSimpleTemplates");
            for (int i = 0; i < promiseSimpleTemplates.length(); i++) {
                jo = promiseSimpleTemplates.getJSONObject(i);
                String templateId = jo.getString("templateId");
                String promiseName = jo.getString("promiseName");
                String status = jo.getString("status");
                if ("un_join".equals(status) && promiseList.getValue().contains(templateId)) {
                    promiseJoin(querySingleTemplate(templateId));
                }
                PromiseSimpleTemplateIdMap.add(templateId, promiseName);
            }
            PromiseSimpleTemplateIdMap.save(UserIdMap.getCurrentUid());
        }
        catch (Throwable t) {
            Log.err(TAG, "promise err:", t);
        }
    }
    
    private JSONObject querySingleTemplate(String templateId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.querySingleTemplate(templateId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            jo = jo.getJSONObject("data");
            JSONObject result = new JSONObject();
            
            result.put("joinFromOuter", false);
            result.put("templateId", jo.getString("templateId"));
            result.put("autoRenewStatus", Boolean.valueOf(jo.getString("autoRenewStatus")));
            
            JSONObject joinGuarantyRule = jo.getJSONObject("joinGuarantyRule");
            joinGuarantyRule.put("selectValue", joinGuarantyRule.getJSONArray("canSelectValues").getString(0));
            joinGuarantyRule.remove("canSelectValues");
            result.put("joinGuarantyRule", joinGuarantyRule);
            
            JSONObject joinRule = jo.getJSONObject("joinRule");
            joinRule.put("selectValue", joinRule.getJSONArray("canSelectValues").getString(0));
            joinRule.remove("joinRule");
            result.put("joinRule", joinRule);
            
            JSONObject periodTargetRule = jo.getJSONObject("periodTargetRule");
            periodTargetRule.put("selectValue", periodTargetRule.getJSONArray("canSelectValues").getString(0));
            periodTargetRule.remove("canSelectValues");
            result.put("periodTargetRule", periodTargetRule);
            
            JSONObject dataSourceRule = jo.getJSONObject("dataSourceRule");
            dataSourceRule.put("selectValue", dataSourceRule.getJSONArray("canSelectValues").getJSONObject(0).getString("merchantId"));
            dataSourceRule.remove("canSelectValues");
            result.put("dataSourceRule", dataSourceRule);
            return result;
        }
        catch (Throwable t) {
            Log.err(TAG, "querySingleTemplate err:", t);
        }
        return null;
    }
    
    private void promiseJoin(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseJoin(data));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            String promiseName = jo.getString("promiseName");
            Log.other("生活记录📝加入[" + promiseName + "]");
        }
        catch (Throwable t) {
            Log.err(TAG, "promiseJoin err:", t);
        }
    }
    
    // 查询持续做明细任务
    private JSONObject promiseQueryDetail(String recordId) throws JSONException {
        JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryDetail(recordId));
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
                JSONObject task = taskList.getJSONObject(i);
                if (task.getBoolean("hybrid")) {
                    int periodCurrentCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_CURRENT_COUNT"));
                    int periodTargetCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_TARGET_COUNT"));
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
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
            String name = taskConfigInfo.getString("name");
            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(name)) {
                return false;
            }
            Long id = taskConfigInfo.getLong("id");
            String awardParamPoint = taskConfigInfo.getJSONObject("awardParam").getString("awardParamPoint");
            String targetBusiness = taskConfigInfo.getJSONArray("targetBusiness").getString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.applyTask(name, id));
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
                jo = new JSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
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
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private void goldTicket() {
        try {
            // 签到
            //已失效
            //goldBillCollect("\"campId\":\"CP1417744\",\"directModeDisableCollect\":true,\"from\":\"antfarm\",");
            // 收取其他
            //goldBillCollect("");
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 芝麻分任务处理（每日问答、公益任务、芭芭农场施肥等）
     */
    private void handleGrowthGuideTasks() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject root = new JSONObject(AntMemberRpcCall.queryGrowthBehaviorToDoList());
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
                    JSONObject openJo = new JSONObject(openResp);
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
                    JSONObject quizJo = new JSONObject(AntMemberRpcCall.queryDailyQuiz(behaviorId));
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
                    JSONObject pushJo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, "RIGHT"));
                    if (MessageUtil.checkResultCode(TAG, pushJo)) {
                        Log.other("攒芝麻分🎖️[每日答题成功] " + StringUtil.truncate(questionContent, 200)
                                + " | 答案=" + StringUtil.truncate(answerContent, 200) + "(" + answerId + ")"
                                + (subTitle.isEmpty() ? "" : " | " + subTitle));
                    }
                }
                
                // 视频问答
                if ("shipingwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    long bizDate = System.currentTimeMillis();
                    String questionId = "question3";
                    String answerId = "A";
                    String answerType = "RIGHT";
                    
                    jo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, answerType));
                    
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("攒芝麻分🎖️[视频问答提交成功]");
                    }
                }
                
                // 芭芭农场施肥
                if ("babanongchang_7d".equals(behaviorId) && "wait_doing".equals(status)) {
                    
                    // 获取WUA
                    String wua = new AntOrchard().getWua();
                    String source = "DNHZ_NC_zhimajingnangSF";
                    
                    JSONObject spreadManureData = new JSONObject(AntOrchardRpcCall.orchardSpreadManure(false, wua));
                    
                    if (!"100".equals(spreadManureData.optString("resultCode"))) {
                        continue;
                    }
                    
                    String taobaoDataStr = spreadManureData.optString("taobaoData", "");
                    if (taobaoDataStr.isEmpty()) {
                        continue;
                    }
                    
                    JSONObject spreadTaobaoData = new JSONObject(taobaoDataStr);
                    
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
            
            JSONObject json = new JSONObject(queryResp);
            
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
            
            JSONObject collectJson = new JSONObject(collectResp);
            int collectedAccelerateProgress = collectJson.optInt("collectedAccelerateProgress", -1);
            int currentAccelerateValue = collectJson.optInt("currentAccelerateValue", 0);
            int totalAccelerateProgress = collectJson.optInt("totalAccelerateProgress", 0);
            Log.other("攒芝麻分🎁领取#本次加速进度:" + collectedAccelerateProgress + "(总" + totalAccelerateProgress + "%)加速倍率:" + currentAccelerateValue);
        }
        catch (JSONException e) {
            Log.printStackTrace(TAG + "queryAndCollect JSON err", e);
        }
        catch (Exception e) {
            Log.printStackTrace(TAG + "queryAndCollect err", e);
        }
    }
    
    /**
     * 收取黄金票
     */
    private void goldBillCollect(String signInfo) {
        try {
            String str = AntMemberRpcCall.goldBillCollect(signInfo);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".goldBillCollect.goldBillCollect", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject object = jsonObject.getJSONObject("result");
            JSONArray jsonArray = object.getJSONArray("collectedList");
            int length = jsonArray.length();
            if (length == 0) {
                return;
            }
            for (int i = 0; i < length; i++) {
                Log.other("黄金票🙈[" + jsonArray.getString(i) + "]");
            }
            Log.other("黄金票🏦本次总共获得[" + JsonUtil.getValueByPath(object, "collectedCamp.amount") + "]");
        }
        catch (Throwable th) {
            Log.err(TAG, "signIn err:", th);
        }
    }
    //游戏中心任务
    
    /**
     * 批量领取玩乐豆
     */
    public static void batchReceivePointBall() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.batchReceivePointBall());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                String totalAmount = dataObj.getString("totalAmount");
                Log.other("游戏中心🎮批量领取#获得[" + totalAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "batchReceivePointBall err:", t);
        }
    }
    
    /**
     * 每日签到
     *
     * @return 签到是否成功
     */
    public static boolean dailySignIn() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.continueSignIn());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject toastModule = jsonObject.getJSONObject("data").getJSONObject("autoSignInToastModule");
                String desc = toastModule.getString("desc");
                String beanNum = desc.substring(desc.indexOf("玩乐豆+") + 4);
                Log.other("游戏中心🎮每日签到#获得[" + beanNum + "玩乐豆]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "continueSignIn err:", t);
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
            if (!"VIEW".equals(taskObj.getString("actionType"))) {
                return;
            }
            
            String taskId = taskObj.getString("taskId");
            String subTitle = taskObj.getString("subTitle");
            String taskStatus = taskObj.getString("taskStatus");
            int prizeAmount = taskObj.getInt("prizeAmount");
            
            // 任务未完成且需要报名
            if ("NOT_DONE".equals(taskStatus) && taskObj.getBoolean("needSignUp")) {
                JSONObject jsonObject = new JSONObject(AntMemberRpcCall.doTaskSignup(taskId));
                if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                    return;
                }
            }
            
            // 执行任务
            JSONObject doTaskjo = new JSONObject(AntMemberRpcCall.doTaskSend(taskId));
            if (MessageUtil.checkSuccess(TAG, doTaskjo)) {
                Log.other("游戏中心🎮完成任务[" + subTitle + "]#待领[" + prizeAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doTask err:", t);
        }
    }
    
    /**
     * 查询并处理任务列表
     */
    public static void queryAndProcessTaskList() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryModularTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONArray taskModuleList = jsonObject.getJSONObject("data").getJSONArray("taskModuleList");
            for (int i = 0; i < taskModuleList.length(); i++) {
                JSONObject moduleObj = taskModuleList.getJSONObject(i);
                JSONArray taskList = moduleObj.getJSONArray("taskList");
                for (int j = 0; j < taskList.length(); j++) {
                    processTask(taskList.getJSONObject(j));
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryModularTaskList err:", t);
        }
    }
    
    public static void queryTaskList() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONArray gameTaskList = jsonObject.getJSONObject("data").optJSONObject("gameTaskModule").optJSONArray("gameTaskList");
            for (int i = 0; i < gameTaskList.length(); i++) {
                processTask(gameTaskList.getJSONObject(i));
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryModularTaskList err:", t);
        }
    }
    
    /**
     * 查询玩乐豆小球列表，有则领取
     */
    public static void queryPointBallList() {
        try {
            String response = ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{}]");
            JSONObject jsonObject = new JSONObject(response);
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONArray pointBallList = jsonObject.getJSONObject("data").getJSONArray("pointBallList");
                if (pointBallList.length() > 0) {
                    batchReceivePointBall();
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointBallList err:", t);
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
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryPointBallList());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                if (dataObj.has("signInBallModule")) {
                    JSONObject signInModule = dataObj.getJSONObject("signInBallModule");
                    if (!signInModule.getBoolean("signInStatus")) {
                        if (dailySignIn()) {
                            Status.flagToday("gameCenterSignIn");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "querySignInBall err:", t);
        }
    }
    
    /*
    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = new JSONObject(str);
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
                jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.record("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.err(TAG, "signIn err:", th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = new JSONObject(str);
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
                jsonObject = new JSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.err(TAG, "batchReceive err:", th);
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
                String raw = AntMemberRpcCall.queryDeliveryZoneDetail(userId, deliveryId);
                jo = new JSONObject(raw);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray entityInfoList = jo.optJSONArray("entityInfoList");
                    if (entityInfoList != null && entityInfoList.length() > 0) {
                        Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 成功，权益数=" + entityInfoList.length());
                        break;
                    }
                }
                Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 失败，尝试下一个");
                jo = null;
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
            // 无条件保存权益列表（供用户勾选）
            java.util.Set<String> selectedIds = memberPointExchangeBenefitList.getValue();
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.getJSONObject(i);
                JSONObject benefitInfo = entityInfo.getJSONObject("benefitInfo");
                JSONObject pricePresentation = benefitInfo.getJSONObject("pricePresentation");
                if (!"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.getString("name");
                String benefitId = benefitInfo.getString("benefitId");
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
                JSONObject entityInfo = entityInfoList.getJSONObject(i);
                JSONObject benefitInfo = entityInfo.getJSONObject("benefitInfo");
                JSONObject pricePresentation = benefitInfo.getJSONObject("pricePresentation");
                if (!"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.getString("name");
                String benefitId = benefitInfo.getString("benefitId");
                // 只兑换用户在列表中勾选的权益
                if (!selectedIds.contains(benefitId)) {
                    continue;
                }
                if (!Status.canMemberPointExchangeBenefitToday(benefitId)) {
                    continue;
                }
                String itemId = benefitInfo.getString("itemId");
                if (exchangeBenefit(benefitId, itemId)) {
                    String point = pricePresentation.getString("point");
                    Log.other("会员积分🎐兑换[" + name + "]#花费[" + point + "积分]");
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "memberPointExchangeBenefit err:", t);
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
                    JSONObject jo = new JSONObject(raw);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " resultCode=" + jo.optString("resultCode") + " desc=" + jo.optString("desc"));
                        break;
                    }
                    JSONArray benefitList = jo.optJSONArray("entityInfoList");
                    if (benefitList == null || benefitList.length() == 0) {
                        benefitList = jo.optJSONObject("data") != null ? jo.getJSONObject("data").optJSONArray("entityInfoList") : null;
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
                        JSONObject entity = benefitList.getJSONObject(i);
                        JSONObject benefitInfo = entity.optJSONObject("benefitInfo");
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
            Log.err(TAG, "fetchBenefitsFromNavi err:", t);
        }
    }
    
    private Boolean exchangeBenefit(String benefitId, String itemId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.exchangeBenefit(benefitId, itemId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "exchangeBenefit err:", t);
        }
        return false;
    }
    
    private void collectSesame() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.getJSONObject("entrance");
            if (!entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }
            
            jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
            for (int i = 0; i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.getString("title") : "未知任务";
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
                
                String taskTemplateId = toCompleteVO.getString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.getInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                String s = null;
                String recordId = null;
                JSONObject responseObj = null;
                
                if (!toCompleteVO.has("todayFinish")) {
                    // 领取任务
                    s = AntMemberRpcCall.joinSesameTask(taskTemplateId);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    TimeUtil.sleep(200);
                    if (!MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.error(TAG + "芝麻信用💳领取任务[" + taskTitle + "]失败#" + s);
                        continue;
                    }
                    recordId = responseObj.getJSONObject("data").getString("recordId");
                }
                else {
                    if (!toCompleteVO.has("recordId")) {
                        Log.error(TAG + "芝麻信用💳任务[" + taskTitle + "未获取到]recordId#" + toCompleteVO);
                        continue;
                    }
                    recordId = toCompleteVO.getString("recordId");
                }
                
                // 完成任务
                for (int j = completedNum; j < needCompleteNum; j++) {
                    s = AntMemberRpcCall.finishSesameTask(recordId);
                    TimeUtil.sleep(2000);
                    responseObj = new JSONObject(s);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, responseObj);
                    
                    if (MessageUtil.checkResultCode(TAG, responseObj)) {
                        Log.record("芝麻信用💳完成任务[" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)");
                    }
                    else {
                        Log.error("芝麻信用💳完成任务[" + taskTitle + "]失败#" + s);
                    }
                }
                
                jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.getJSONArray("creditFeedbackVOS");
                for (int j = 0; j < ja.length(); j++) {
                    jo = ja.getJSONObject(j);
                    if (!"UNCLAIMED".equals(jo.getString("status"))) {
                        continue;
                    }
                    //String title = jo.getString("title");
                    String creditFeedbackId = jo.getString("creditFeedbackId");
                    String potentialSize = jo.getString("potentialSize");
                    jo = new JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }
            jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.getJSONArray("creditFeedbackVOS");
            if (creditFeedbackVOS.length() != 0) {
                jo = new JSONObject(AntMemberRpcCall.collectAllCreditFeedback());
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
        // 领取是否失败：失败时不置今日标记，留给下一轮重试（否则当天不再重试 → 漏领）
        boolean claimFailed = false;
        try {
            
            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = new JSONObject(checkInRes);
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
                                JSONObject completeJo = new JSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    claimFailed = true;
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                claimFailed = true;
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            if (claimFailed) {
                Log.other("收芝麻粒🙇🏻‍♂️签到领取失败#本轮不置今日标记，稍后重试");
            }
            else {
                Status.flagToday("AntMember::zmlCheckIn");
            }
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
            JSONObject jsonResponse = new JSONObject(response);
            // 获取 taskDetailList 数组
            JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
            // 遍历 taskDetailList
            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);
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
                String taskId = taskMaterial.getString("taskId");
                // 调用 trigger 方法
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = new JSONObject(triggerResponse);
                // 检查 success 字段
                boolean success = triggerResult.getBoolean("success");
                if (success) {
                    // 从 triggerResponse 中获取 prizeSendInfo 数组
                    JSONArray prizeSendInfo = triggerResult.getJSONArray("prizeSendInfo");
                    if (prizeSendInfo.length() > 0) {
                        JSONObject prizeInfo = prizeSendInfo.getJSONObject(0);
                        JSONObject extInfo = prizeInfo.getJSONObject("extInfo");
                        // 获取 promoCampName
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        // 输出日志信息
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.err(TAG, "RecommendTask err:", th);
        }
    }
    
    private void OrdinaryTask() {
        try {
            // 调用 AntMemberRpcCall.queryOrdinaryTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = new JSONObject(response);
            // 检查是否请求成功
            if (jsonResponse.getBoolean("success")) {
                // 获取任务详细列表
                JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
                // 遍历任务详细列表
                for (int i = 0; i < taskDetailList.length(); i++) {
                    // 获取当前任务对象
                    JSONObject task = taskDetailList.getJSONObject(i);
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
                        JSONObject sendTriggerJson = new JSONObject(sendtriggerResponse);
                        // 判断任务是否成功
                        if (sendTriggerJson.getBoolean("success")) {
                            // 从 sendtriggerResponse 中获取 prizeSendInfo 数组
                            JSONArray prizeSendInfo = sendTriggerJson.getJSONArray("prizeSendInfo");
                            // 获取 prizeName
                            String prizeName = prizeSendInfo.getJSONObject(0).getString("prizeName");
                            Log.other("我的快递💌完成[" + prizeName + "]");
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
            Log.err(TAG, "OrdinaryTask err:", th);
        }
    }
}
