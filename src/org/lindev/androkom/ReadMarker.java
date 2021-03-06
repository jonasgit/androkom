package org.lindev.androkom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import nu.dll.lyskom.Selection;
import nu.dll.lyskom.TextStat;
import android.util.Log;

public class ReadMarker {
	private static final String TAG = "Androkom ReadMarker";

    private final KomServer mKom;
    private final Map<Integer, Integer> mMarked;
    private final BlockingQueue<Integer> mToMark;

    public ReadMarker(final KomServer kom) {
        this.mKom = kom;
        this.mMarked = new ConcurrentHashMap<Integer, Integer>();
        this.mToMark = new LinkedBlockingQueue<Integer>();
        new MarkerThread().start();
    }

    private class MarkerThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "MarkerThread begun");
            while (!isInterrupted()) {
                int textNo = -1;
                try {
                    textNo = mToMark.take();
                } catch (final InterruptedException e) {
                    // Someone called interrupt()
                    continue;
                }
                if (textNo > 0) {
                    markToServer(textNo);
                }
            }
            Log.d(TAG, "MarkerThread done");
        }
    }

    private void markToServer(final int textNo) {
        Log.i(TAG, "Mark as read: " + textNo);
        if(!mKom.isConnected()) {
            Log.d(TAG, " markToServer not connected");
            return;
        }
        try {
            final TextStat stat = mKom.getTextStat(textNo, true);
            if(stat==null) {
                Log.d(TAG, "Failed to getTextStat");
                return;
            }
            final int[] tags = { TextStat.miscRecpt, TextStat.miscCcRecpt, TextStat.miscBccRecpt };
            List<Selection> recipientSelections = new ArrayList<Selection>();
            for (final int tag : tags) {
                recipientSelections.addAll(stat.getMiscInfoSelections(tag));
            }
            for (final Selection selection : recipientSelections) {
                int rcpt = 0;
                for (int tag : tags) {
                    if (selection.contains(tag)) {
                        rcpt = selection.getIntValue(tag);
                    }
                }
                if (rcpt > 0) {
                    int local = selection.getIntValue(TextStat.miscLocNo);
                    Log.i(TAG, "markAsRead: global " + textNo + " rcpt " + rcpt + " local " + local);
                    mKom.markAsRead(rcpt, new int[] { local });
                }
            }
        }
        catch (NullPointerException e) {
            Log.d(TAG, "markToServer Handled a NullPointerException:"+e);
            //e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "markToServer Handled a InterruptedException:"+e);
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "markToServer Handled an Exception:"+e);
            e.printStackTrace();
        }
        Log.i(TAG, "Mark as read finished: " + textNo);
    }

    public void mark(final int textNo) {
        boolean needServerMark = !isLocalRead(textNo);
        if (!needServerMark) {
            Log.i(TAG, "markAsRead mark: no server mark for " + textNo);
            return;
        }
        synchronized (mMarked) {
            Log.i(TAG, "markAsRead mark: adding text as marked on server " + textNo);
            needServerMark = (mMarked.put(textNo, textNo) == null);
        }
        if (needServerMark) {
            synchronized (mToMark) {
                Log.i(TAG, "markAsRead mark: adding text to queue " + textNo);
                mToMark.add(textNo);
            }
        } else {
            Log.i(TAG, "markAsRead mark: no 2-server mark for " + textNo);
        }
    }

    public boolean isLocalRead(final int textNo) {
        return mMarked.containsKey(textNo);
    }

    public void clear() {
        Log.i(TAG, "ReadMarker clear()");
        synchronized (mMarked) {
            mMarked.clear();
        }
    }
}
