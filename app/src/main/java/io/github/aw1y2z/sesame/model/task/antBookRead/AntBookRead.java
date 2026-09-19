package io.github.aw1y2z.sesame.model.task.antBookRead;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.StringUtil;

public class AntBookRead extends ModelTask {
    private static final String TAG = AntBookRead.class.getSimpleName();

    @Override
    public String getName() {
        return "读书听书";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME || !TaskCommon.IS_AFTER_8AM) {
            return false;
        }
        long executeTime = RuntimeInfo.getInstance().getLong("consumeGold", 0);
        return System.currentTimeMillis() - executeTime >= 21600000;
    }

    @Override
    public void run() {
        try {
            RuntimeInfo.getInstance().put("consumeGold", System.currentTimeMillis());
            queryTaskCenterPage();
            queryTask();
            queryTreasureBox();
        } catch (Throwable t) {
            Log.err(TAG, "start.run err:", t);
        }
    }

    /**
     * 从文案里取数字：服务端文案一变，取到的就是空串或非数字，原先直接 Integer.parseInt 会抛
     * NumberFormatException 并中断整个模块；这里改为返回 -1（调用方按「取不到」处理）。
     */
    private static int parseOrMinusOne(String text, String left, String right) {
        Integer parsed = StringUtil.parseIntOrNull(StringUtil.getSubString(text, left, right));
        if (parsed == null) {
            Log.i(TAG, "解析数字失败[" + left + ".." + right + "]: " + text);
            return -1;
        }
        return parsed;
    }

    private static void queryTaskCenterPage() {
        try {
            String s = AntBookReadRpcCall.queryTaskCenterPage();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject data = jo.optJSONObject("data");
                JSONObject benefitAggBlock = data != null ? data.optJSONObject("benefitAggBlock") : null;
                String todayPlayDurationText = benefitAggBlock != null ? benefitAggBlock.optString("todayPlayDurationText") : "";
                int PlayDuration = parseOrMinusOne(todayPlayDurationText, "今日听读时长", "分钟");
                if (PlayDuration < 450) {
                    jo = MyUtils.newJSONObject(AntBookReadRpcCall.queryHomePage());
                    if (jo.optBoolean("success")) {
                        JSONObject homeData = jo.optJSONObject("data");
                        JSONArray dynamicCardList = homeData != null ? homeData.optJSONArray("dynamicCardList") : null;
                        JSONObject firstCard = dynamicCardList != null ? dynamicCardList.optJSONObject(0) : null;
                        JSONObject cardData = firstCard != null ? firstCard.optJSONObject("data") : null;
                        JSONArray bookList = cardData != null ? cardData.optJSONArray("bookList") : null;
                        if (bookList == null || bookList.length() == 0) {
                            return;
                        }
                        int bookListLength = bookList.length();
                        int postion = RandomUtil.nextInt(0, bookListLength - 1);
                        JSONObject book = bookList.optJSONObject(postion);
                        String bookId = book != null ? book.optString("bookId") : null;
                        if (bookId == null) {
                            return;
                        }
                        jo = MyUtils.newJSONObject(AntBookReadRpcCall.queryReaderContent(bookId));
                        if (jo.optBoolean("success")) {
                            JSONObject contentData = jo.optJSONObject("data");
                            String nextChapterId = contentData != null ? contentData.optString("nextChapterId") : "";
                            JSONObject readerHomePageVO = contentData != null ? contentData.optJSONObject("readerHomePageVO") : null;
                            String name = readerHomePageVO != null ? readerHomePageVO.optString("name") : "";
                            for (int i = 0; i < 17; i++) {
                                int energy = 0;
                                jo = MyUtils.newJSONObject(AntBookReadRpcCall.syncUserReadInfo(bookId, nextChapterId));
                                if (jo.optBoolean("success")) {
                                    jo = MyUtils.newJSONObject(AntBookReadRpcCall.queryReaderForestEnergyInfo(bookId));
                                    if (jo.optBoolean("success")) {
                                        JSONObject energyData = jo.optJSONObject("data");
                                        String tips = energyData != null ? energyData.optString("tips") : "";
                                        if (tips.contains("已得")) {
                                            energy = parseOrMinusOne(tips, "已得", "g");
                                        }
                                        Log.forest("阅读书籍📚[" + name + "]#累计能量" + energy + "g");
                                    }
                                }
                                if (energy >= 150) {
                                    break;
                                } else {
                                    Thread.sleep(1500L);
                                }
                            }
                        }
                    }
                }
            } else {
                Log.record(jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTaskCenterPage err:", t);
        }
    }

    private static void queryTask() {
        boolean doubleCheck = false;
        try {
            String s = AntBookReadRpcCall.queryTaskCenterPage();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject data = jo.optJSONObject("data");
                JSONObject userTaskListModuleVO = data != null ? data.optJSONObject("userTaskListModuleVO") : null;
                JSONArray userTaskGroupList = userTaskListModuleVO != null ? userTaskListModuleVO.optJSONArray("userTaskGroupList") : null;
                for (int i = 0; userTaskGroupList != null && i < userTaskGroupList.length(); i++) {
                    jo = userTaskGroupList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    JSONArray userTaskList = jo.optJSONArray("userTaskList");
                    for (int j = 0; userTaskList != null && j < userTaskList.length(); j++) {
                        JSONObject taskInfo = userTaskList.optJSONObject(j);
                        if (taskInfo == null) {
                            continue;
                        }
                        String taskStatus = taskInfo.optString("taskStatus");
                        String taskType = taskInfo.optString("taskType");
                        String title = taskInfo.optString("title");
                        if ("TO_RECEIVE".equals(taskStatus)) {
                            if ("READ_MULTISTAGE".equals(taskType)) {
                                JSONArray multiSubTaskList = taskInfo.optJSONArray("multiSubTaskList");
                                for (int k = 0; multiSubTaskList != null && k < multiSubTaskList.length(); k++) {
                                    taskInfo = multiSubTaskList.optJSONObject(k);
                                    if (taskInfo == null) {
                                        continue;
                                    }
                                    taskStatus = taskInfo.optString("taskStatus");
                                    if ("TO_RECEIVE".equals(taskStatus)) {
                                        String taskId = taskInfo.optString("taskId");
                                        collectTaskPrize(taskId, taskType, title);
                                    }
                                }
                            } else {
                                String taskId = taskInfo.optString("taskId");
                                collectTaskPrize(taskId, taskType, title);
                            }
                        } else if ("NOT_DONE".equals(taskStatus)) {
                            if ("AD_VIDEO_TASK".equals(taskType)) {
                                String taskId = taskInfo.optString("taskId");
                                for (int m = 0; m < 5; m++) {
                                    taskFinish(taskId, taskType);
                                    Thread.sleep(1500L);
                                    collectTaskPrize(taskId, taskType, title);
                                    Thread.sleep(1500L);
                                }
                            } else if ("FOLLOW_UP".equals(taskType) || "JUMP".equals(taskType)) {
                                String taskId = taskInfo.optString("taskId");
                                taskFinish(taskId, taskType);
                                doubleCheck = true;
                            }
                        }
                    }
                }
                if (doubleCheck)
                    queryTask();
            } else {
                Log.record(jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTask err:", t);
        }
    }

    private static void collectTaskPrize(String taskId, String taskType, String name) {
        try {
            String s = AntBookReadRpcCall.collectTaskPrize(taskId, taskType);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject data = jo.optJSONObject("data");
                int coinNum = data != null ? data.optInt("coinNum") : 0;
                Log.other("阅读任务📖[" + name + "]#" + coinNum);
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectTaskPrize err:", t);
        }
    }

    private static void taskFinish(String taskId, String taskType) {
        try {
            String s = AntBookReadRpcCall.taskFinish(taskId, taskType);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {

            }
        } catch (Throwable t) {
            Log.err(TAG, "taskFinish err:", t);
        }
    }

    private static void queryTreasureBox() {
        try {
            String s = AntBookReadRpcCall.queryTreasureBox();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject data = jo.optJSONObject("data");
                JSONObject treasureBoxVo = data != null ? data.optJSONObject("treasureBoxVo") : null;
                if (treasureBoxVo == null || treasureBoxVo.has("countdown"))
                    return;
                String status = treasureBoxVo.optString("status");
                if ("CAN_OPEN".equals(status)) {
                    jo = MyUtils.newJSONObject(AntBookReadRpcCall.openTreasureBox());
                    if (jo.optBoolean("success")) {
                        JSONObject openData = jo.optJSONObject("data");
                        int coinNum = openData != null ? openData.optInt("coinNum") : 0;
                        Log.other("阅读任务📖[打开宝箱]#" + coinNum);
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTreasureBox err:", t);
        }
    }
}
