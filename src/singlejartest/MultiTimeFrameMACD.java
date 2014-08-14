package singlejartest;

import com.dukascopy.api.*;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;

public class MultiTimeFrameMACD implements IStrategy {

    private static final int macdFast = 8;
    private static final int macdSlow = 17;
    private static final int macdSignal = 9;

    private static final int shortSignalTime = 15;

    private IEngine engine = null;
    private IIndicators indicators = null;
    private IConsole console;
    private Twitter twitter;
    private boolean shortNotifySignal = false;
    private boolean longNotifySignal = false;
    private boolean trendNotifySignal = false;
    private SignalInfo shortTimeNotify = null;
    private SignalInfo longTimeNotify = null;
    private SignalInfo trendTimeNotify = null;

    public void onStart(IContext context) throws JFException {
        engine = context.getEngine();
        indicators = context.getIndicators();
        this.console = context.getConsole();
        console.getOut().println("Started");
        twitter = new TwitterFactory().getInstance();
    }

    public void onStop() throws JFException {
        for (IOrder order : engine.getOrders()) {
            order.close();
        }
        console.getOut().println("Stopped");
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        Map<Period, double[]> indicatorsValue = new HashMap<Period, double[]>();
        Map<Period, Integer> directionValue = new HashMap<Period, Integer>();
        Period[] usePeriod = {Period.ONE_MIN, Period.FIVE_MINS, Period.FIFTEEN_MINS, Period.THIRTY_MINS, Period.ONE_HOUR, Period.FOUR_HOURS, Period.DAILY};

        for (Period p : usePeriod) {
            indicatorsValue.put(p, indicators.macd(instrument, p, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, macdFast, macdSlow, macdSignal, 0));
            directionValue.put(p, checkMACDSignal(indicatorsValue.get(p)));
        }

        int MTFdirection = 0;

        MTFdirection = checkMTFDirection(directionValue);
        tweetDirectionNotification(directionValue);

        if (MTFdirection == 2 || MTFdirection == -2) {
            System.out.println(MTFdirection + "  方向にシグナル発生中。");
        }
//        JsonObjectBuilder fxBuilder = Json.createObjectBuilder();
//        fxBuilder.add("Symbol", getLabel(instrument));
//        for (Period p : usePeriod) {
//            String s = "";
//            if (directionValue.get(p) == 2) {
//                s = "↑↑";
//            } else if (directionValue.get(p) == 1) {
//                s = "↑";
//            } else if (directionValue.get(p) == -1) {
//                s = "↓";
//            } else if (directionValue.get(p) == -2) {
//                s = "↓↓";
//            }
//            fxBuilder.add(p.name(), s);
//        }
//        fxBuilder.add("time", new Date(tick.getTime()).toLocaleString());
//        fxBuilder.add("realtime", new Date().toLocaleString());
        //System.out.println(fxBuilder.build());

    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onAccount(IAccount account) throws JFException {
    }

    /**
     * *
     * MTFでの方向を返す。
     *
     * @param directionValue 各時間軸のDirection値。
     * @return
     */
    private int checkMTFDirection(Map<Period, Integer> directionValue) {
        int ans = 2;
        for (int i : directionValue.values()) {
            if (i != 2) {
                ans = 0;
            }
        }
        if (ans == 2) {
            return ans;
        }
        ans = -2;
        for (int i : directionValue.values()) {
            if (i != -2) {
                ans = 0;
            }
        }
        if (ans == -2) {
            return ans;
        }
        return 0;
    }

    /**
     * MACDの方向を値で返す＋方向＝Long、－方向＝short値が大きいほど強い
     *
     * @param macd
     * @return
     */
    private int checkMACDSignal(double[] macd) {
        if (macd[0] > 0 && macd[1] > 0 && macd[0] > macd[1]) {
            return 2;
        } else if (macd[0] > macd[1]) {
            return 1;
        }
        if (macd[0] < 0 && macd[1] < 0 && macd[0] < macd[1]) {
            return -2;
        } else if (macd[0] < macd[1]) {
            return -1;
        }
        return 0;
    }

    protected String getLabel(Instrument instrument) {
        String label = instrument.name();
        return label;
    }

    /**
     * *
     * tweetする。
     *
     * @param tweetString tweetメッセージ。
     * @return
     */
    private Status tweet(String tweetString) {
        Status s = null;
        try {
            s = twitter.updateStatus("サンプルTweet");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    /**
     * 各時間軸の方向に従ってTweetをする。
     *
     * @param directionValue
     */
    private void tweetDirectionNotification(Map<Period, Integer> directionValue) {
        Calendar nowTime = Calendar.getInstance();
        
        Calendar sTime = Calendar.getInstance();
        sTime.setTime(shortTimeNotify.time);

        Calendar lTime = Calendar.getInstance();
        lTime.setTime(longTimeNotify.time);

        
        
        sTime.add(Calendar.MINUTE, shortSignalTime);
        if (sTime.getTime().getTime() > nowTime.getTime().getTime()) {
            if (directionValue.get(Period.ONE_MIN) == 2
                    && directionValue.get(Period.FIVE_MINS) == 2
                    && directionValue.get(Period.FIFTEEN_MINS) == 2) {
                tweet("短期時間軸（1分足、5分足、15分足）MACDがLong(買い)傾向になりました。");
                shortTimeNotify.time = new Date();
                shortTimeNotify.direction = 2;
            }else if(directionValue.get(Period.ONE_MIN) == -2
                    && directionValue.get(Period.FIVE_MINS) == -2
                    && directionValue.get(Period.FIFTEEN_MINS) == -2  ){
                tweet("短期時間軸（1分足、5分足、15分足）MACDがShort(売り)傾向になりました。");
            }

        }

        //集計
        for (Period p : directionValue.keySet()) {

        }
    }

    class SignalInfo {

        public Date time;
        public int direction;
        public boolean isNotify = false;

        public SignalInfo(Date time, int direction) {
            this.time = time;
            this.direction = direction;
        }

    }
}
