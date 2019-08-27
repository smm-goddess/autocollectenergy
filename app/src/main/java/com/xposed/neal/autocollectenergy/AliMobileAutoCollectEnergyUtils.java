package com.xposed.neal.autocollectenergy;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;

import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.COLLECT_ENERGY;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.HELP_COLLECT_ENERGY;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.QUERY_FRIEND_ACTION;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.QUERY_FRIEND_RANKING;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.QUERY_PAGE_DYNAMICS;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyConst.QUERY_PK_RECORDS;

class AliMobileAutoCollectEnergyUtils {
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private static String TAG = "XposedHookZFB";
    private static ArrayList<CollectData> collectData = new ArrayList<>();
    private static long mostRecentCollectTime = Long.MAX_VALUE;
    static ClassLoader loader;
    private static ArrayList<String> friendsWhiteListId = new ArrayList<String>() ;
    private static Integer totalEnergy = 0;
    private static Integer totalHelpEnergy = 0;
    private static Integer pageCount = 0;
    private static Object curH5PageImpl;
    static WeakReference<Object> curH5FragmentRef;

    static void DiagnoseRpcHookParams(XC_MethodHook.MethodHookParam param) {
        Object[] args = param.args;
        // Log.i(TAG, "params:" + args[0] + "," + args[1] + "," + args[2] + "," + args[3]
//                + "," + args[4] + "," + args[5] + "," + args[6] + "," + "H5" + "," + args[8]
//                + "," + args[9] + "," + args[10] + "," + args[11] + "," + args[12]);
        String funcName = (String) args[0];
        String jsonArgs = (String) args[1];
        switch (funcName) {
            case QUERY_FRIEND_RANKING:
                if (parseFriendRankPageDataResponse(parseResponseData(param.getResult()))) {
                    rpcCall_QueryFriendRanking();
                } else {
                    pageCount = 0;
                    showToast("开始获取每个好友能够偷取的能量信息...");
                    for (CollectData data : collectData) {
                        rpcCall_QueryFriendPage(data);
                    }
                    postCollect();
                }
                break;
            case QUERY_FRIEND_ACTION:
                if (!jsonArgs.contains("userId")) {
                    // 刚打开页面,请求不带userId,获取的是自己的数据
                    parseCollectBubbles(parseResponseData(param.getResult()));
                    rpcCall_QueryFriendRanking();
                } else {
                    // 其他用户的能量球信息
                    parseCollectBubbles(parseResponseData(param.getResult()));
                }
                break;
            case HELP_COLLECT_ENERGY:
                parseHelpCollectEnergyResponse(parseResponseData(param.getResult()));
                break;
            case COLLECT_ENERGY:
                parseCollectEnergyResponse(parseResponseData(param.getResult()));
                break;
            default:
                break;
        }
    }

    private static void postCollect() {
        showToast("一共收取了" + totalEnergy + "g能量" + ",帮助收取了" + totalHelpEnergy + "g能量");
        totalEnergy = 0;
        totalHelpEnergy = 0;
        if (mostRecentCollectTime != Long.MAX_VALUE) {
            long nextCollectTime = Long.max(System.currentTimeMillis(), mostRecentCollectTime) + 1000;
            showToast("下次收集时间:" + simpleDateFormat.format(new Date(nextCollectTime)));
//            new Handler().postAtTime(new Runnable() {
//                @Override
//                public void run() {
//                    restartCollect();
//                }
//            }, nextCollectTime);
            mostRecentCollectTime = Long.MAX_VALUE;
        }
    }

    private static void restartCollect() {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("version", "20181220");
            jsonArray.put(json);
            // Log.i(TAG, "call restartCollect energy params:" + jsonArray);
            rpcCall("alipay.antmember.forest.h5.queryNextAction", jsonArray.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void rpcCall_QueryFriendRanking() {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("av", "5");
            json.put("ct", "android");
            json.put("pageSize", pageCount * 20);
            json.put("startPoint", "" + (pageCount * 20 + 1));
            pageCount++;
            jsonArray.put(json);
            showToast("开始获取可以收取第" + pageCount + "页好友信息的能量...");
            // Log.i(TAG, "rpcCall_QueryFriendRanking params:" + jsonArray);

            rpcCall(QUERY_FRIEND_RANKING, jsonArray.toString());
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_QueryFriendRanking err: " + Log.getStackTraceString(e));
        }
    }

    private static void rpcCall_QueryFriendPage(CollectData data) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("canRobFlags", data.getRobFlags());
            json.put("userId", data.collectUserId);
            json.put("version", "20181220");
            jsonArray.put(json);
            // Log.i(TAG, "call cancollect energy params:" + jsonArray);

            rpcCall(QUERY_FRIEND_ACTION, jsonArray.toString());

            JSONArray pkArray = new JSONArray();
            JSONObject pkObject = new JSONObject();
            pkObject.put("pkType", "Week");
            pkObject.put("pkUser", data.collectUserId);
            pkArray.put(pkObject);
            rpcCall(QUERY_PK_RECORDS, pkArray.toString());

            JSONArray jArray = new JSONArray();
            JSONObject jObject = new JSONObject();
            jObject.put("pageSize", 10);
            jObject.put("startIndex", 0);
            jObject.put("userId", data.collectUserId);
            jArray.put(jObject);
            rpcCall(QUERY_PAGE_DYNAMICS, jArray.toString());
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_CanCollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    private static void parseCollectBubbles(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
            if (jsonArray != null && jsonArray.length() > 0) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject1 = jsonArray.getJSONObject(i);
                    String userId = jsonObject1.optString("userId");
                    String collectStatus = jsonObject1.optString("collectStatus");
                    if (!friendsWhiteListId.contains(userId)) {
                        if ("AVAILABLE".equals(collectStatus)) {
                            rpcCall_CollectEnergy(jsonObject1.optString("userId"), jsonObject1.optLong("id"));
                        }
                    }
                    if ("INSUFFICIENT".equals(collectStatus) && jsonObject1.optBoolean("canHelpCollect")) {
                        rpcCall_HelpCollectEnergy(jsonObject1.optString("userId"), jsonObject1.optLong("id"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 解析好友信息
     *
     * @param response
     * @return
     */
    private static boolean parseFriendRankPageDataResponse(String response) {
        try {
            JSONObject jObject = new JSONObject(response);
            JSONArray optJSONArray = jObject.optJSONArray("friendRanking");
            if (optJSONArray != null) {
                for (int i = 0; i < optJSONArray.length(); i++) {
                    JSONObject jsonObject = optJSONArray.getJSONObject(i);
                    boolean canCollect = jsonObject.optBoolean("canCollectEnergy");
                    boolean canHelpCollect = jsonObject.optBoolean("canHelpCollect");
                    String userId = jsonObject.optString("userId");
                    if (!friendsWhiteListId.contains(userId)) {
                        long collectLaterTime = jsonObject.optLong("canCollectLaterTime");
                        if (collectLaterTime != -1 && mostRecentCollectTime > collectLaterTime) {
                            mostRecentCollectTime = collectLaterTime;
                        }
                        CollectData data = new CollectData(userId, canCollect, canHelpCollect, collectLaterTime != -1);
                        if ((canCollect || canHelpCollect) && !collectData.contains(data)) {
                            collectData.add(data);
                        }
                    }
                }
            }
            return jObject.optBoolean("hasMore");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String parseResponseData(Object resp) {
        try {
            Method method = resp.getClass().getMethod("getResponse", new Class<?>[]{});
            return (String) method.invoke(resp, new Object[]{});
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static void rpcCall(String funcName, String jsonArrayString) {
        try {
            Method rpcCallMethod = getRpcCallMethod();
            Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
            Object obj = jsonClazz.newInstance();
            rpcCallMethod.invoke(null, funcName, jsonArrayString,
                    "", true, obj, null, false, curH5PageImpl, 0, "", false, -1, "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 帮助收取能量命令
     *
     * @param userId
     * @param bubbleId
     */
    private static void rpcCall_HelpCollectEnergy(String userId, long bubbleId) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONArray bubbleAry = new JSONArray();
            bubbleAry.put(bubbleId);
            JSONObject json = new JSONObject();
            json.put("targetUserId", userId);
            json.put("bubbleIds", bubbleAry);
            jsonArray.put(json);
            // Log.i(TAG, "call HelpCollectEnergy energy params:" + jsonArray);

            rpcCall(HELP_COLLECT_ENERGY, jsonArray.toString());
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_HelpCollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    /**
     * 收取能量命令
     *
     * @param userId
     * @param bubbleId
     */
    private static void rpcCall_CollectEnergy(String userId, long bubbleId) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONArray bubbleAry = new JSONArray();
            bubbleAry.put(bubbleId);
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("bubbleIds", bubbleAry);
            jsonArray.put(json);
            // Log.i(TAG, "call rpcCall_CollectEnergy energy params:" + jsonArray);

            rpcCall(COLLECT_ENERGY, jsonArray.toString());
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_CollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    private static Method getRpcCallMethod() {
        Object curH5Fragment = curH5FragmentRef.get();
        if (curH5Fragment != null) {
            try {
                Field aF = curH5Fragment.getClass().getDeclaredField("a");
                aF.setAccessible(true);
                Object viewHolder = aF.get(curH5Fragment);
                Field hF = viewHolder.getClass().getDeclaredField("h");
                hF.setAccessible(true);
                curH5PageImpl = hF.get(viewHolder);
                Class<?> h5PageClazz = loader.loadClass("com.alipay.mobile.h5container.api.H5Page");
                Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
                Class<?> rpcClazz = loader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil");
                if (curH5PageImpl != null) {
                    Method callM = rpcClazz.getMethod("rpcCall", String.class, String.class, String.class,
                            boolean.class, jsonClazz, String.class, boolean.class, h5PageClazz,
                            int.class, String.class, boolean.class, int.class, String.class);
                    return callM;
                }
            } catch (Exception e) {
                // Log.i(TAG, "getRpcCallMethod err: " + Log.getStackTraceString(e));
            }
        }
        return null;
    }

    private static void parseHelpCollectEnergyResponse(String response) {
        if (!TextUtils.isEmpty(response) && response.contains("failedBubbleIds")) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
                for (int i = 0; i < jsonArray.length(); i++) {
                    totalHelpEnergy += jsonArray.getJSONObject(i).optInt("collectedEnergy");
                }
            } catch (Exception e) {
            }
        }
    }

    private static void parseCollectEnergyResponse(String response) {
        if (!TextUtils.isEmpty(response) && response.contains("failedBubbleIds")) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
                for (int i = 0; i < jsonArray.length(); i++) {
                    totalEnergy += jsonArray.getJSONObject(i).optInt("collectedEnergy");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Activity getActivity() {
        final Object h5Fragment = curH5FragmentRef.get();
        if (h5Fragment != null) {
            try {
                Method getActivity = h5Fragment.getClass().getMethod("getActivity");
                getActivity.setAccessible(true);
                return (Activity) getActivity.invoke(h5Fragment);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    private static void showToast(final String str) {
        final Activity activity = getActivity();
        if (activity != null) {
            try {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(activity, str, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                // Log.i(TAG, "showToast err: " + Log.getStackTraceString(e));
            }
        }
    }

    private static class CollectData {
        String collectUserId;
        boolean canCollect;
        boolean canHelpCollect;
        boolean canCollectAfter;

        CollectData(String collectUserId, boolean canCollect, boolean canHelpCollect, boolean canCollectAfter) {
            this.collectUserId = collectUserId;
            this.canCollect = canCollect;
            this.canHelpCollect = canHelpCollect;
            this.canCollectAfter = canCollectAfter;
        }

        String getRobFlags() {
            String flags;
            if (canCollect) {
                flags = "T";
            } else {
                flags = "F";
            }
            if (canHelpCollect) {
                flags = flags + ",T";
            } else {
                flags = flags + ",F";
            }
            if (canCollectAfter) {
                flags = flags + ",T";
            } else {
                flags = flags + ",F";
            }
            return flags;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CollectData) {
                return collectUserId.equals(((CollectData) obj).collectUserId);
            } else {
                return false;
            }
        }
    }

}
