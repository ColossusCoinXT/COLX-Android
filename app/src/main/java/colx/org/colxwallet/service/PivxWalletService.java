package colx.org.colxwallet.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;

import org.colxj.core.Block;
import org.colxj.core.Coin;
import org.colxj.core.FilteredBlock;
import org.colxj.core.Peer;
import org.colxj.core.Transaction;
import org.colxj.core.TransactionConfidence;
import org.colxj.core.listeners.AbstractPeerDataEventListener;
import org.colxj.core.listeners.PeerConnectedEventListener;
import org.colxj.core.listeners.PeerDataEventListener;
import org.colxj.core.listeners.PeerDisconnectedEventListener;
import org.colxj.core.listeners.TransactionConfidenceEventListener;
import org.colxj.store.BlockStore;
import org.colxj.wallet.Wallet;
import org.colxj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import chain.BlockchainManager;
import chain.BlockchainState;
import chain.Impediment;
import colx.org.colxwallet.AppPreference;
import colx.org.colxwallet.ColxApplication;
import colx.org.colxwallet.HardcodedConstants;
import colx.org.colxwallet.R;
import colx.org.colxwallet.module.PivxContext;
import colx.org.colxwallet.module.PivxModuleImp;
import colx.org.colxwallet.module.store.SnappyBlockchainStore;
import colx.org.colxwallet.rate.CoinMarketCapApiClient;
import colx.org.colxwallet.rate.RequestPivxRateException;
import colx.org.colxwallet.rate.db.PivxRate;
import colx.org.colxwallet.ui.initial.InitialActivity;
import colx.org.colxwallet.ui.wallet_activity.WalletActivity;
import colx.org.colxwallet.utils.AppConf;
import pivtrum.listeners.AddressListener;

import static colx.org.colxwallet.module.PivxContext.CONTEXT;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_ADDRESS_BALANCE_CHANGE;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_BROADCAST_TRANSACTION;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_CANCEL_COINS_RECEIVED;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_NOTIFICATION;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_RESET_BLOCKCHAIN;
import static colx.org.colxwallet.service.IntentsConstants.ACTION_SCHEDULE_SERVICE;
import static colx.org.colxwallet.service.IntentsConstants.DATA_TRANSACTION_HASH;
import static colx.org.colxwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_BLOCKCHAIN_STATE;
import static colx.org.colxwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_ON_COIN_RECEIVED;
import static colx.org.colxwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_PEER_CONNECTED;
import static colx.org.colxwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_TYPE;
import static colx.org.colxwallet.service.IntentsConstants.INTENT_EXTRA_BLOCKCHAIN_STATE;
import static colx.org.colxwallet.service.IntentsConstants.NOT_BLOCKCHAIN_ALERT;
import static colx.org.colxwallet.service.IntentsConstants.NOT_COINS_RECEIVED;

/**
 * Created by furszy on 6/12/17.
 */

public class PivxWalletService extends Service{

    private Logger log = LoggerFactory.getLogger(PivxWalletService.class);

    private ColxApplication pivxApplication;
    private PivxModuleImp module;
    //private PivtrumPeergroup pivtrumPeergroup;
    private BlockchainManager blockchainManager;

    private PeerConnectivityListener peerConnectivityListener;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager nm;
    private LocalBroadcastManager broadcastManager;

    private SnappyBlockchainStore blockchainStore;
    private boolean resetBlockchainOnShutdown = false;
    /** Created service time (just for checks) */
    private long serviceCreatedAt;
    /** Cached amount to notify balance */
    private Coin notificationAccumulatedAmount = Coin.ZERO;
    /**  */
    private final Set<Impediment> impediments = EnumSet.noneOf(Impediment.class);

    private BlockchainState blockchainState = BlockchainState.NOT_CONNECTION;

    private volatile long lastUpdateTime = System.currentTimeMillis();
    private volatile long lastMessageTime = System.currentTimeMillis();

    public class PivxBinder extends Binder {
        public PivxWalletService getService() {
            return PivxWalletService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PivxBinder();
    }

    private AddressListener addressListener = new AddressListener() {
        @Override
        public void onBalanceChange(String address, long confirmed, long unconfirmed,int numConfirmations) {
            Intent intent = new Intent(ACTION_ADDRESS_BALANCE_CHANGE);
            broadcastManager.sendBroadcast(intent);
        }
    };

    private final class PeerConnectivityListener implements PeerConnectedEventListener, PeerDisconnectedEventListener{
        @Override
        public void onPeerConnected(Peer peer, int i) {
            //todo: notify peer connected
            log.info("Peer connected: "+peer.getAddress());
            broadcastPeerConnected();
        }
        @Override
        public void onPeerDisconnected(Peer peer, int i) {
            //todo: notify peer disconnected
            log.info("Peer disconnected: "+peer.getAddress());
        }
    }

    private final PeerDataEventListener blockchainDownloadListener = new AbstractPeerDataEventListener() {
        @Override
        public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock, final int blocksLeft) {
            try {
                log.info("Block received , left: " + blocksLeft);

//            log.info("############# on Blockcs downloaded ###########");
//            log.info("Peer: " + peer + ", Block: " + block + ", left: " + blocksLeft);


            /*if (PivxContext.IS_TEST)
                showBlockchainSyncNotification(blocksLeft);*/

                //delayHandler.removeCallbacksAndMessages(null);


                final long now = System.currentTimeMillis();

                if (now - lastMessageTime > TimeUnit.SECONDS.toMillis(6)) {
                    if (blocksLeft < 6) {
                        blockchainState = BlockchainState.SYNC;
                        AppPreference.setInt(AppPreference.SYNC_STATE, AppPreference.SYNC_SYNC);
                    } else {
                        blockchainState = BlockchainState.SYNCING;
                        AppPreference.setInt(AppPreference.SYNC_STATE, AppPreference.SYNC_SYNCING);
                    }

                    pivxApplication.getAppConf().setLastBestChainBlockTime(block.getTime().getTime());
                    broadcastBlockchainState(true);
                } else {
                    if(blocksLeft < 6){
                        blockchainState = BlockchainState.SYNC;
                        AppPreference.setInt(AppPreference.SYNC_STATE, AppPreference.SYNC_SYNC);

                    pivxApplication.getAppConf().setLastBestChainBlockTime(block.getTime().getTime());
                    broadcastBlockchainState(true);
                }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private class RunnableBlockChecker implements Runnable{

        private Block block;

        public RunnableBlockChecker(Block block) {
            this.block = block;
        }

        @Override
        public void run() {
            org.colxj.core.Context.propagate(PivxContext.CONTEXT);
            lastMessageTime = System.currentTimeMillis();
            broadcastBlockchainState(false);
        }
    }

    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.info("connectivityReceiver");
            try {
                final String action = intent.getAction();
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    final NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    final boolean hasConnectivity = networkInfo.isConnected();
                    log.info("network is {}, state {}/{}", hasConnectivity ? "up" : "down", networkInfo.getState(), networkInfo.getDetailedState());
                    if (hasConnectivity)
                        impediments.remove(Impediment.NETWORK);
                    else
                        impediments.add(Impediment.NETWORK);
                    check();
                    // try to request coin rate
                    requestRateCoin();
                } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                    log.info("device storage low");

                    impediments.add(Impediment.STORAGE);
                    check();
                } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                    log.info("device storage ok");
                    impediments.remove(Impediment.STORAGE);
                    check();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private WalletCoinsReceivedEventListener coinReceiverListener = new WalletCoinsReceivedEventListener() {

        android.support.v4.app.NotificationCompat.Builder mBuilder;
        PendingIntent deleteIntent;
        PendingIntent openPendingIntent;

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction transaction, Coin coin, Coin coin1) {
            //todo: acá falta una validación para saber si la transaccion es mia.
            org.colxj.core.Context.propagate(CONTEXT);

            try {

                int depthInBlocks = transaction.getConfidence().getDepthInBlocks();

                long now = System.currentTimeMillis();
                if (lastUpdateTime + 5000 < now) {
                    lastUpdateTime = now;
                    Intent intent = new Intent(ACTION_NOTIFICATION);
                    intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_ON_COIN_RECEIVED);
                    broadcastManager.sendBroadcast(intent);
                }

                //final Address address = WalletUtils.getWalletAddressOfReceived(WalletConstants.NETWORK_PARAMETERS,transaction, wallet);
                final Coin amount = transaction.getValue(wallet);
                final TransactionConfidence.ConfidenceType confidenceType = transaction.getConfidence().getConfidenceType();

                if (amount.isGreaterThan(Coin.ZERO)) {
                    //notificationCount++;
                    notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
                    Intent openIntent = new Intent(getApplicationContext(), WalletActivity.class);
                    openPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, openIntent, 0);
                    Intent resultIntent = new Intent(getApplicationContext(), PivxWalletService.this.getClass());
                    resultIntent.setAction(ACTION_CANCEL_COINS_RECEIVED);
                    deleteIntent = PendingIntent.getService(PivxWalletService.this, 0, resultIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    mBuilder = new NotificationCompat.Builder(getApplicationContext())
                            .setContentTitle("COLX received!")
                            .setContentText("Coins received for a value of " + notificationAccumulatedAmount.toFriendlyString())
                            .setAutoCancel(true)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setColor(
                                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ?
                                            getResources().getColor(R.color.bgPurple, null)
                                            :
                                            ContextCompat.getColor(PivxWalletService.this, R.color.bgPurple))
                            .setDeleteIntent(deleteIntent)
                            .setContentIntent(openPendingIntent);
                    nm.notify(NOT_COINS_RECEIVED, mBuilder.build());
                } else {
                    log.error("transaction with a value lesser than zero arrives ({})", amount.getValue());
                }

            }catch (Exception e){
                e.printStackTrace();
            }

        }
    };

    private TransactionConfidenceEventListener transactionConfidenceEventListener = new TransactionConfidenceEventListener() {
        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction transaction) {
            org.colxj.core.Context.propagate(CONTEXT);
            try {
                if (transaction != null) {
                    if (transaction.getConfidence().getDepthInBlocks() > 1) {
                        long now = System.currentTimeMillis();
                        if (lastUpdateTime + 5000 < now) {
                            lastUpdateTime = now;
                            // update balance state
                            Intent intent = new Intent(ACTION_NOTIFICATION);
                            intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_ON_COIN_RECEIVED);
                            broadcastManager.sendBroadcast(intent);
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        super.onCreate();

        initService();
    }

    private static final String ANDROID_CHANNEL_ID = "colx.org.colxwallet.service.PivxWalletService.Channel";
    private static final int NOTIFICATION_ID = 1;
    private void showForegroundNotification (){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(ANDROID_CHANNEL_ID, "Channel human readable title", NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

//            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ANDROID_CHANNEL_ID);
//            NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
//            bigTextStyle.setBigContentTitle("COLX wallet is syncing.");
////            bigTextStyle.bigText("Tap for more information or to stop the app.");
//            bigTextStyle.bigText("");
//            builder.setStyle(bigTextStyle);
//            builder.setSmallIcon(R.mipmap.ic_launcher);
//            builder.setLargeIcon(largeIconBitmap);
//            builder.setPriority(Notification.PRIORITY_MAX);

            Notification.Builder builder = new Notification.Builder(this, ANDROID_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("COLX wallet is syncing.")
                    .setLargeIcon(largeIconBitmap)
                    .setAutoCancel(true);

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
        }
    }
    private void initService(){
        log.info("Pivx service init");
        try {
            // Android stuff
            final String lockName = getPackageName() + " blockchain sync";
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);
            nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            broadcastManager = LocalBroadcastManager.getInstance(this);
            // Pivx
            pivxApplication = ColxApplication.getInstance();
            module = (PivxModuleImp) pivxApplication.getModule();
            blockchainManager = module.getBlockchainManager();
            // connect to pivtrum node
            /*pivtrumPeergroup = new PivtrumPeergroup(pivxApplication.getNetworkConf());
            pivtrumPeergroup.addAddressListener(addressListener);
            module.setPivtrumPeergroup(pivtrumPeergroup);*/

            // Schedule service
            tryScheduleService();

            peerConnectivityListener = new PeerConnectivityListener();

            File file = getDir("blockstore_v2",MODE_PRIVATE);
            String filename = PivxContext.Files.BLOCKCHAIN_FILENAME;
            boolean fileExists = new File(file,filename).exists();
            blockchainStore = new SnappyBlockchainStore(PivxContext.CONTEXT,file,filename);
            blockchainManager.init(
                    blockchainStore,
                    file,
                    filename,
                    fileExists
            );

            module.addCoinsReceivedEventListener(coinReceiverListener);
            module.addOnTransactionConfidenceChange(transactionConfidenceEventListener);

            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
            registerReceiver(connectivityReceiver, intentFilter); // implicitly init PeerGroup
        } catch (Error e){
            e.printStackTrace();
            Intent intent = new Intent(IntentsConstants.ACTION_STORED_BLOCKCHAIN_ERROR);
            broadcastManager.sendBroadcast(intent);
            throw e;
        } catch (Exception e){
            // todo: I have to handle the connection refused..
            e.printStackTrace();
            // for now i just launch a notification
            Intent intent = new Intent(IntentsConstants.ACTION_TRUSTED_PEER_CONNECTION_FAIL);
            broadcastManager.sendBroadcast(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.info("Pivx service onStartCommand");

        showForegroundNotification();

        try {
            if (intent != null) {
                try {
                    log.info("service init command: " + intent + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));
                } catch (Exception e) {
                    e.printStackTrace();
                    log.info("service init command: " + intent + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getLongArrayExtra(Intent.EXTRA_ALARM_COUNT) + ")" : ""));
                }
                final String action = intent.getAction();
                if (ACTION_SCHEDULE_SERVICE.equals(action)) {
                    check();
                } else if (ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
                    notificationAccumulatedAmount = Coin.ZERO;
                    nm.cancel(NOT_COINS_RECEIVED);
                } else if (ACTION_RESET_BLOCKCHAIN.equals(action)) {
                    log.info("will remove blockchain on service shutdown");
                    resetBlockchainOnShutdown = true;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        stopForeground(true);
                    } else {
                        stopSelf();
                    }

                } else if (ACTION_BROADCAST_TRANSACTION.equals(action)) {
                    blockchainManager.broadcastTransaction(intent.getByteArrayExtra(DATA_TRANSACTION_HASH));
                }
            } else {
                log.warn("service restart, although it was started as non-sticky");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.info("onDestroy()");

        stopService();
    }

    public void stopService(){
        log.info("Pivx service stopService");

        try {
            // todo: notify module about this shutdown...
            unregisterReceiver(connectivityReceiver);

            // remove listeners
            module.removeCoinsReceivedEventListener(coinReceiverListener);
            module.removeTransactionsConfidenceChange(transactionConfidenceEventListener);
            blockchainManager.removeBlockchainDownloadListener(blockchainDownloadListener);
            // destroy the blockchain
            /*if (resetBlockchainOnShutdown){
                try {
                    blockchainStore.truncate();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }*/
            blockchainManager.destroy(resetBlockchainOnShutdown);

            /*if (pivtrumPeergroup.isRunning()) {
                pivtrumPeergroup.shutdown();
            }*/

            if (wakeLock.isHeld()) {
                log.debug("wakelock still held, releasing");
                wakeLock.release();
            }

            log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
            // schedule service it is not scheduled yet
            tryScheduleService();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Schedule service for later
     */
    private void tryScheduleService() {
        boolean isSchedule = System.currentTimeMillis()<module.getConf().getScheduledBLockchainService();

        if (!isSchedule){
            log.info("scheduling service");
            AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
            long scheduleTime = System.currentTimeMillis() + 2000*60;//(1000 * 60 * 60); // One hour from now
            Date now = new Date();
            scheduleTime = now.getTime() + 2 * 1000 * 60;//(1000 * 60 * 60); // One hour from now

            Intent intent = new Intent(this, PivxWalletService.class);
            intent.setAction(ACTION_SCHEDULE_SERVICE);
            alarm.set(
                    // This alarm will wake up the device when System.currentTimeMillis()
                    // equals the second argument value
                    alarm.RTC_WAKEUP,
                    scheduleTime,
                    // PendingIntent.getService creates an Intent that will start a service
                    // when it is called. The first argument is the Context that will be used
                    // when delivering this intent. Using this has worked for me. The second
                    // argument is a request code. You can use this code to cancel the
                    // pending intent if you need to. Third is the intent you want to
                    // trigger. In this case I want to create an intent that will start my
                    // service. Lastly you can optionally pass flags.
                    PendingIntent.getService(this, 0,intent , 0)
            );
            // save
            module.getConf().saveScheduleBlockchainService(scheduleTime);
        }
    }

    private void requestRateCoin(){
        final AppConf appConf = pivxApplication.getAppConf();
        PivxRate pivxRate = module.getRate(appConf.getSelectedRateCoin());
        if (pivxRate==null || pivxRate.getTimestamp()+PivxContext.RATE_UPDATE_TIME<System.currentTimeMillis()){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        CoinMarketCapApiClient c = new CoinMarketCapApiClient();
                        CoinMarketCapApiClient.PivxMarket pivxMarket = c.getPivxPxrice();
                        PivxRate pivxRate = new PivxRate("USD",pivxMarket.priceUsd,System.currentTimeMillis());
                        module.saveRate(pivxRate);
                        final PivxRate pivxBtcRate = new PivxRate("BTC",pivxMarket.priceBtc,System.currentTimeMillis());
                        module.saveRate(pivxBtcRate);

                        // Get the rest of the rates:
                        List<PivxRate> rates = new CoinMarketCapApiClient.BitPayApi().getRates(new CoinMarketCapApiClient.BitPayApi.RatesConvertor<PivxRate>() {
                            @Override
                            public PivxRate convertRate(String code, String name, BigDecimal bitcoinRate) {
                                BigDecimal rate = bitcoinRate.multiply(pivxBtcRate.getRate());
                                return new PivxRate(code,rate,System.currentTimeMillis());
                            }
                        });

                        for (PivxRate rate : rates) {
                            module.saveRate(rate);
                        }

                    } catch (RequestPivxRateException e) {
                        e.printStackTrace();
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private AtomicBoolean isChecking = new AtomicBoolean(false);

    /**
     * Check and download the blockchain if it needed
     */
    private void check() {
        log.info("check");
        try {
            if (!isChecking.getAndSet(true)) {
                blockchainManager.check(
                        impediments,
                        peerConnectivityListener,
                        peerConnectivityListener,
                        blockchainDownloadListener);
                //todo: ver si conviene esto..
                broadcastBlockchainState(true);
                isChecking.set(false);
            }
        }catch (Exception e){
            e.printStackTrace();
            isChecking.set(false);
            broadcastBlockchainState(false);
        }
    }

    private void broadcastBlockchainState(boolean isCheckOk) {
        boolean showNotif = false;
        if (!impediments.isEmpty()) {

            StringBuilder stringBuilder = new StringBuilder();
            for (Impediment impediment : impediments) {
                if (stringBuilder.length()!=0){
                    stringBuilder.append("\n");
                }
                if (impediment == Impediment.NETWORK){
                    blockchainState = BlockchainState.NOT_CONNECTION;
                    stringBuilder.append("No peer connection");
                }else if(impediment == Impediment.STORAGE){
                    stringBuilder.append("No available storage");
                    showNotif = true;
                }
            }

            if(showNotif) {
                android.support.v4.app.NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("Alert")
                                .setContentText(stringBuilder.toString())
                                .setAutoCancel(true)
                                .setColor(
                                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ?
                                                getResources().getColor(R.color.bgPurple,null)
                                                :
                                                ContextCompat.getColor(PivxWalletService.this,R.color.bgPurple))
                        ;

                nm.notify(NOT_BLOCKCHAIN_ALERT, mBuilder.build());
            }
        }

        if (isCheckOk){
            broadcastBlockchainStateIntent();
        }
    }

    private void broadcastBlockchainStateIntent(){
        final long now = System.currentTimeMillis();
        if (now-lastMessageTime> TimeUnit.SECONDS.toMillis(6) || blockchainState == BlockchainState.SYNC ) {
            lastMessageTime = System.currentTimeMillis();
            Intent intent = new Intent(ACTION_NOTIFICATION);
            intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_BLOCKCHAIN_STATE);
            intent.putExtra(INTENT_EXTRA_BLOCKCHAIN_STATE,blockchainState);
            broadcastManager.sendBroadcast(intent);
        }

        if(blockchainState == BlockchainState.SYNC){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(true);
            } else {
                stopSelf();
            }
        }
    }

    private void broadcastPeerConnected() {
        Intent intent = new Intent(ACTION_NOTIFICATION);
        intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_PEER_CONNECTED);
        broadcastManager.sendBroadcast(intent);
    }

    @SuppressLint("NewApi")
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log.info("onTaskRemoved");

        if( blockchainState != BlockchainState.SYNC ) {
//            Date now = new Date();
//            Alarms.enableAlert(getApplicationContext(), now.getTime() + 1000, HardcodedConstants.ALARM_REQ_CODE);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
//                Intent intentService = new Intent(getApplicationContext(), PivxWalletBackgroundService.class);
//                startService(intentService );

                Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
                restartServiceIntent.setPackage(getPackageName());
                PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
                AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
            }
        }

        super.onTaskRemoved(rootIntent);
    }
}
