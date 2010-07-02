package com.kwogger.rootedshoutbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

//TODO fix background colour issue
public final class RootEdShoutBox extends ListActivity implements
        OnClickListener, OnEditorActionListener, Handler.Callback
{
    private static class ListTask extends AsyncTask<Object, String, Object>
    {
        private ArrayAdapter<String> adapter;
        private RootEdShoutBox self;

        private ListTask(RootEdShoutBox self, ArrayAdapter<String> adapter)
        {
            this.self = self;
            this.adapter = adapter;
        }

        @Override
        protected Object doInBackground(Object... params)
        {
            final CharSequence oldStatus = self.info.getText();
            self.handler.obtainMessage(MSG_STATUS, R.string.status_userlist, 0)
                    .sendToTarget();
            HttpURLConnection connection = null;
            BufferedReader rd = null;
            InputStream is = null;
            try
            {
                URL url = new URL(self.getString(R.string.url_userlist, self
                        .getString(R.string.url_base)));
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
                {
                    rd = new BufferedReader(new InputStreamReader(
                            is = connection.getInputStream()), 2048);
                    String line;
                    while ((line = rd.readLine()) != null
                            && !line.contains("<p>Currently online:</p>"))
                        ;
                    rd.readLine();
                    while ((line = rd.readLine()) != null
                            && !(line = line.trim()).contains("</ul>"))
                        publishProgress(Html.fromHtml(
                                line.substring(4, line.length() - 5))
                                .toString());
                    while ((line = rd.readLine()) != null
                            && !line.contains("<p>List of users:</p>"))
                        ;
                    rd.readLine();
                    while ((line = rd.readLine()) != null
                            && !(line = line.trim()).contains("</ul>"))
                        publishProgress(Html.fromHtml(
                                line.substring(4, line.length() - 5))
                                .toString());
                    while (rd.readLine() != null)
                        ;
                }
            }
            catch (MalformedURLException e)
            {
                self.handler.obtainMessage(MSG_TOAST,
                        R.string.error_userlist_url, 0).sendToTarget();
                Log.e(TAG, "MalformedURLException when loading user list", e);
            }
            catch (IOException e)
            {
                self.handler.obtainMessage(MSG_TOAST,
                        R.string.error_userlist_io, 0).sendToTarget();
                Log.e(TAG, e.getMessage(), e);
            }
            finally
            {
                self.handler.obtainMessage(MSG_STATUS, oldStatus)
                        .sendToTarget();
                if (rd != null)
                    try
                    {
                        rd.close();
                    }
                    catch (Exception e)
                    {
                    }
                if (is != null)
                    try
                    {
                        is.close();
                    }
                    catch (Exception e)
                    {
                    }
                if (connection != null)
                    try
                    {
                        connection.disconnect();
                    }
                    catch (Exception e)
                    {
                    }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result)
        {
            adapter = null;
            self = null;
        }

        @Override
        protected void onProgressUpdate(String... values)
        {
            adapter.add(values[0]);
        }
    }

    private static class SendTask extends AsyncTask<Object, Object, Boolean>
    {
        private RootEdShoutBox self;

        private SendTask(RootEdShoutBox self)
        {
            this.self = self;
        }

        @Override
        protected Boolean doInBackground(Object... params)
        {
            Boolean ret = true;
            self.handler.obtainMessage(MSG_STATUS, R.string.status_sending, 0)
                    .sendToTarget();
            if (self.code == null)
                self.handler.obtainMessage(MSG_TOAST,
                        R.string.error_send_nocode, 0).sendToTarget();
            else if (self.message.getText().toString().length() < 2)
                self.handler.obtainMessage(MSG_TOAST,
                        R.string.error_send_morechar, 0).sendToTarget();
            else
            {
                String name = self.prefs.getString(self
                        .getString(R.string.pref_name_key), self
                        .getString(R.string.pref_name_def));
                if (name.length() < 1)
                    name = self.getString(R.string.pref_name_def);
                String data = "live=yes&code="
                        + encodeURIComponent(self.code)
                        + "&name="
                        + encodeURIComponent(name)
                        + "&url="
                        + encodeURIComponent(self.prefs.getString(self
                                .getString(R.string.pref_website_key), self
                                .getString(R.string.pref_website_def)))
                        + "&message="
                        + encodeURIComponent(self.message.getText().toString());
                HttpURLConnection connection = null;
                OutputStreamWriter wr = null;
                BufferedReader rd = null;
                try
                {
                    URL url = new URL(self.getString(R.string.url_send, self
                            .getString(R.string.url_base)));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.connect();
                    wr = new OutputStreamWriter(connection.getOutputStream());
                    wr.write(data);
                    wr.flush();
                    rd = new BufferedReader(new InputStreamReader(connection
                            .getInputStream()), 256);
                    String line;
                    while ((line = rd.readLine()) != null
                            && !line.equals("redirect"))
                    {
                        if (line.equals("output"))
                            self.handler.obtainMessage(MSG_TOAST,
                                    R.string.error_send_banned, 0)
                                    .sendToTarget();
                        else
                            self.handler.obtainMessage(MSG_TOAST,
                                    R.string.error_send_unknown, 0)
                                    .sendToTarget();
                        // TODO add support for unknown error reporting 
                        ret = false;
                    }
                    while (rd.readLine() != null)
                        ;
                }
                catch (MalformedURLException e)
                {
                    self.handler.obtainMessage(MSG_TOAST,
                            R.string.error_send_url, 0).sendToTarget();
                    Log.e(TAG, e.getMessage(), e);
                }
                catch (IOException e)
                {
                    self.handler.obtainMessage(MSG_TOAST,
                            R.string.error_send_io, 0).sendToTarget();
                    Log.e(TAG, e.getMessage(), e);
                }
                finally
                {
                    if (wr != null)
                        try
                        {
                            wr.close();
                        }
                        catch (Exception e)
                        {
                        }
                    if (rd != null)
                        try
                        {
                            rd.close();
                        }
                        catch (Exception e)
                        {
                        }
                    if (connection != null)
                        try
                        {
                            connection.disconnect();
                        }
                        catch (Exception e)
                        {
                        }
                }
            }
            self.sent = true;
            self.handler.sendEmptyMessage(MSG_UPDATE);
            return ret;
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            if (result)
                self.message.setText("");
            self.message.setEnabled(true);
            self.send.setEnabled(true);
            self = null; // prevent memory leaks
        }

        @Override
        protected void onPreExecute()
        {
            self.send.setEnabled(false);
            self.message.setEnabled(false);
        }
    }

    private static class UpdateTask extends
            AsyncTask<Object, ShoutBoxItem, Object> implements Runnable
    {
        private static final int IMG = 1;
        private static final int LINK = 2;
        private static final int SPACER = 3;
        private static final int TEXT = 0;
        private ShoutBoxAdapter adapter;
        private int index;
        private long lastId;
        private boolean newData;
        private boolean notify;
        private RootEdShoutBox self;
        private volatile URL updateURL;

        private UpdateTask(RootEdShoutBox self)
        {
            this.self = self;
            this.adapter = (ShoutBoxAdapter) self.getListAdapter();
        }

        @Override
        protected Object doInBackground(Object... params)
        {
            try
            {
                HttpURLConnection connection = null;
                InputStream is = null;
                XmlPullParserFactory factory = XmlPullParserFactory
                        .newInstance();
                factory.setNamespaceAware(false);
                XmlPullParser xpp = factory.newPullParser();
                if (self.code == null)
                {
                    // Get the "code" to send shouts
                    Message.obtain(self.handler, MSG_STATUS,
                            R.string.status_connecting, 0).sendToTarget();
                    BufferedReader rd = null;
                    try
                    {
                        URL url = new URL(self.getString(R.string.url_code,
                                self.getString(R.string.url_base)));
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        rd = new BufferedReader(new InputStreamReader(
                                is = connection.getInputStream()), 4096);
                        String line = null;
                        int index = -1;
                        while (index == -1 && (line = rd.readLine()) != null)
                            index = line
                                    .indexOf("<input name=\"code\" type=\"hidden\""
                                            + " id=\"code\" value=\"");
                        if (index != -1)
                        {
                            line = line.substring(index + 50);
                            self.code = line.substring(0, line.indexOf('"'));
                        }
                        while (rd.readLine() != null)
                            ;
                    }
                    catch (MalformedURLException e)
                    {
                        Message.obtain(self.handler, MSG_TOAST,
                                R.string.error_code_url, 0).sendToTarget();
                        Log.e(TAG, e.getMessage(), e);
                    }
                    catch (IOException e)
                    {
                        Message.obtain(self.handler, MSG_TOAST,
                                R.string.error_code_io, 0).sendToTarget();
                        Log.e(TAG, e.getMessage(), e);
                    }
                    finally
                    {
                        if (rd != null)
                            try
                            {
                                rd.close();
                                rd = null;
                            }
                            catch (Exception e)
                            {
                            }
                        if (is != null)
                            try
                            {
                                is.close();
                                is = null;
                            }
                            catch (Exception e)
                            {
                            }
                        if (connection != null)
                            try
                            {
                                connection.disconnect();
                                connection = null;
                            }
                            catch (Exception e)
                            {
                            }
                    }
                    if (self.code == null)
                    {
                        Message.obtain(self.handler, MSG_STATUS,
                                R.string.status_noconnect, 0).sendToTarget();
                        self.handler.sendEmptyMessage(MSG_SCHEDULE_UPDATE_NEXT);
                        return null;
                    }
                    else
                        Message.obtain(self.handler, MSG_STATUS,
                                R.string.status_connect, 0).sendToTarget();
                }
                if (self.code != null)
                {
                    try
                    {
                        self.handler.postAtFrontOfQueue(this);
                        synchronized (this)
                        {
                            try
                            {
                                wait(1000);
                            }
                            catch (InterruptedException e)
                            {
                            }
                        }
                        if (updateURL == null)
                        {
                            self.handler.obtainMessage(MSG_TOAST,
                                    R.string.error_update_loadurl, 0)
                                    .sendToTarget();
                            return null;
                        }
                        (connection = (HttpURLConnection) updateURL
                                .openConnection()).connect();
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
                        {
                            xpp
                                    .setInput(is = connection.getInputStream(),
                                            null);
                            int eventType = xpp.getEventType();
                            String tag = null;
                            long id = -1;
                            boolean status = false;
                            String ip = null;
                            String time = null;
                            String href = null;
                            int type = -1;
                            SpannableStringBuilder content = new SpannableStringBuilder();
                            while (eventType != XmlPullParser.END_DOCUMENT)
                            {
                                switch (eventType)
                                {
                                case XmlPullParser.START_TAG:
                                    tag = xpp.getName().toLowerCase().intern();
                                    if (tag == "shoutbox")
                                    {
                                        Message
                                                .obtain(
                                                        self.handler,
                                                        MSG_STATUS,
                                                        xpp.getAttributeValue(
                                                                null, "time")
                                                                + ", "
                                                                + xpp
                                                                        .getAttributeValue(
                                                                                null,
                                                                                "online")
                                                                + " online")
                                                .sendToTarget();
                                        self.prefs
                                                .edit()
                                                .putInt(
                                                        self
                                                                .getString(R.string.pref_pages_key),
                                                        Integer
                                                                .parseInt(xpp
                                                                        .getAttributeValue(
                                                                                null,
                                                                                "pages")))
                                                .commit();
                                    }
                                    else if (tag == "post")
                                    {
                                        id = Long.parseLong(xpp
                                                .getAttributeValue(null, "id"));
                                        status = xpp.getAttributeValue(null,
                                                "status")
                                                .equalsIgnoreCase("on");
                                    }
                                    else if (tag == "user")
                                    {
                                        ip = xpp
                                                .getAttributeValue(null, "data");
                                        href = xpp.getAttributeValue(null,
                                                "href");
                                    }
                                    else if (tag == "message")
                                    {
                                        time = xpp.getAttributeValue(null,
                                                "time");
                                    }
                                    else if (tag == "item")
                                    {
                                        String str = xpp.getAttributeValue(
                                                null, "type");
                                        if (str == null)
                                            type = TEXT;
                                        else
                                        {
                                            str = str.toLowerCase().intern();
                                            if (str == "smiley")
                                            {
                                                type = IMG;
                                                href = xpp.getAttributeValue(
                                                        null, "href");
                                            }
                                            else if (str == "url")
                                                type = LINK;
                                            else if (str == "spacer")
                                            {
                                                type = SPACER;
                                                content.append(' ');
                                            }
                                        }
                                    }
                                    break;
                                case XmlPullParser.END_TAG:
                                    if (xpp.getName().equalsIgnoreCase("post"))
                                        publishProgress(new ShoutBoxItem(id,
                                                status, ip, time,
                                                new SpannedString(content)));
                                    tag = null;
                                    break;
                                case XmlPullParser.TEXT:
                                    if (tag == "user")
                                    {
                                        content.clear();
                                        content
                                                .append(xpp.getText())
                                                .setSpan(
                                                        href == null ? new StyleSpan(
                                                                Typeface.BOLD)
                                                                : new URLSpan(
                                                                        href),
                                                        0,
                                                        content.length(),
                                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        content.append(": ");
                                    }
                                    else if (tag == "item")
                                    {
                                        switch (type)
                                        {
                                        case TEXT:
                                            content.append(xpp.getText());
                                            break;
                                        case IMG:
                                            String smileyText = xpp.getText();
                                            Drawable smiley = ShoutBoxItem.GETTER
                                                    .getDrawable(href);
                                            if (smiley == null)
                                                content.append(smileyText);
                                            else
                                                content
                                                        .append(smileyText)
                                                        .setSpan(
                                                                new ImageSpan(
                                                                        smiley,
                                                                        href),
                                                                content
                                                                        .length()
                                                                        - smileyText
                                                                                .length(),
                                                                content
                                                                        .length(),
                                                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            break;
                                        case LINK:
                                            String link = xpp.getText();
                                            content
                                                    .append("[link]")
                                                    .setSpan(
                                                            new URLSpan(link),
                                                            content.length() - 6,
                                                            content.length(),
                                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            break;
                                        }
                                    }
                                    break;
                                }
                                eventType = xpp.next();
                            }
                        }
                    }
                    catch (MalformedURLException e)
                    {
                        Message.obtain(self.handler, MSG_TOAST,
                                R.string.error_update_url, 0).sendToTarget();
                        Log.e(TAG, e.getMessage(), e);
                    }
                    catch (IOException e)
                    {
                        Message.obtain(self.handler, MSG_TOAST,
                                R.string.error_update_io, 0).sendToTarget();
                        Log.e(TAG, e.getMessage(), e);
                    }
                    catch (XmlPullParserException e)
                    {
                        Message.obtain(self.handler, MSG_TOAST,
                                R.string.error_update_parse, 0).sendToTarget();
                        Log.e(TAG, e.getMessage(), e);
                    }
                    finally
                    {
                        if (is != null)
                            try
                            {
                                is.close();
                                is = null;
                            }
                            catch (Exception e)
                            {
                            }
                        if (connection != null)
                            try
                            {
                                connection.disconnect();
                                connection = null;
                            }
                            catch (Exception e)
                            {
                            }
                    }
                }
            }
            catch (XmlPullParserException e)
            {
                Message.obtain(self.handler, MSG_TOAST,
                        R.string.error_update_parser, 0).sendToTarget();
                Log.e(TAG, "XmlPullParserException when initializing", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result)
        {
            if (self.prefs.getBoolean(self.getString(R.string.pref_delold_key),
                    false))
            {
                ShoutBoxItem item;
                for (int i = adapter.getCount() - 1; i >= 0; --i)
                {
                    item = adapter.getItem(i);
                    if (item.id < lastId)
                    {
                        adapter.remove(item);
                        newData = true;
                    }
                    else
                        break;
                }
            }

            if (self.justOpened)
                self.justOpened = false;
            else if (notify
                    && self.prefs.getBoolean(self
                            .getString(R.string.pref_notify_key), false))
            {
                NotificationManager nm = (NotificationManager) self
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                Notification n = new Notification(R.drawable.notify, null,
                        System.currentTimeMillis());
                n.setLatestEventInfo(self, null, null, PendingIntent
                        .getActivity(self, 0, new Intent(self,
                                RootEdShoutBox.class), 0));
                n.flags |= Notification.FLAG_AUTO_CANCEL;
                String sound = self.prefs.getString(self
                        .getString(R.string.pref_sound_key), null);
                if (sound != null)
                    n.sound = Uri.parse(sound);
                if (self.prefs.getBoolean(
                        self.getString(R.string.pref_led_key), false))
                    n.defaults |= Notification.DEFAULT_LIGHTS;
                if (self.prefs.getBoolean(self
                        .getString(R.string.pref_vibrate_key), false))
                    n.vibrate = VIBRATE_PATTERN;
                nm.notify(NOTIFY_ID, n);
                nm.cancel(NOTIFY_ID);
            }
            self.handler.sendEmptyMessage(newData ? MSG_SCHEDULE_UPDATE_RESET
                    : MSG_SCHEDULE_UPDATE_NEXT);
            self.setProgressBarIndeterminateVisibility(false);
            updateURL = null;
            adapter = null;
            self = null;
        }

        @Override
        protected void onPreExecute()
        {
            self.setProgressBarIndeterminateVisibility(true);
            lastId = -1;
            notify = false;
            newData = false;
            index = 0;
        }

        @Override
        protected void onProgressUpdate(ShoutBoxItem... values)
        {
            int i;
            if ((i = adapter.getPosition(values[0])) == -1)
            {
                adapter.insert(values[0], index++);
                if (self.sent)
                    self.sent = false;
                else
                    notify = true;
                newData = true;
            }
            else
            {
                ShoutBoxItem item = adapter.getItem(i);
                if (item.status != values[0].status)
                {
                    item.status = values[0].status;
                    newData = true;
                }
            }
            lastId = values[0].id;
        }

        @Override
        public void run()
        {
            if (adapter != null)
            {
                try
                {
                    updateURL = (adapter.getCount() > 2) ? new URL(self
                            .getString(R.string.url_update, self
                                    .getString(R.string.url_base))
                            + adapter.getItem(0).id
                            + "&lid=p"
                            + adapter.getItem(adapter.getCount() - 1).id)
                            : new URL(self.getString(R.string.url_update_new,
                                    self.getString(R.string.url_base)));
                }
                catch (MalformedURLException e)
                {
                    Log.e(TAG, "Malformed URL when updating", e);
                }
            }
            synchronized (this)
            {
                notify();
            }
        }
    }

    private static final int DIALOG_LIST = 100;
    private static final int DIALOG_NAME = 101;

    private static final int MSG_SCHEDULE_UPDATE_NEXT = 200;
    private static final int MSG_SCHEDULE_UPDATE_RESET = 201;
    private static final int MSG_STATUS = 202;
    private static final int MSG_TOAST = 203;
    private static final int MSG_UPDATE = 204;
    private static final int MSG_NO_NAME = 205;

    private static final int NOTIFY_ID = 400;

    private static final int REQUEST_HISTORY = 300;
    private static final int REQUEST_PREFS = 301;

    private static final String STATE_CODE_KEY = "code";

    private static final String TAG = "RYDShoutBox";

    private static final long[] VIBRATE_PATTERN = { 0, 200, 200, 200 };

    /**
     * Encodes the passed String as UTF-8 using an algorithm that's compatible
     * with JavaScript's <code>encodeURIComponent</code> function. Returns
     * <code>null</code> if the String is <code>null</code>.
     * 
     * @param s
     *            The String to be encoded
     * @return the encoded String
     */
    public static String encodeURIComponent(String s)
    {
        String result = null;
        try
        {
            result = URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!").replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(").replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        }
        // This exception should never occur.
        catch (UnsupportedEncodingException e)
        {
            result = s;
        }
        return result;
    }

    private String code;
    private final Handler handler = new Handler(this);
    private TextView info;
    private volatile boolean justOpened;
    private ListTask listTask;
    private EditText message;
    private SharedPreferences prefs;
    private ImageButton send;
    private SendTask sendTask;
    private volatile boolean sent;
    private int sleepTime;
    private UpdateTask updateTask;

    @Override
    public boolean handleMessage(Message msg)
    {
        switch (msg.what)
        {
        case MSG_SCHEDULE_UPDATE_RESET:
            handler.sendEmptyMessageDelayed(MSG_UPDATE, sleepTime = 1000);
            return true;
        case MSG_SCHEDULE_UPDATE_NEXT:
            handler.sendEmptyMessageDelayed(MSG_UPDATE,
                    sleepTime < 32000 ? sleepTime <<= 1 : sleepTime);
            return true;
        case MSG_UPDATE:
            if (updateTask == null
                    || updateTask.getStatus() == AsyncTask.Status.FINISHED)
            {
                handler.removeMessages(MSG_UPDATE);
                (updateTask = new UpdateTask(this)).execute((Object[]) null);
            }
            return true;
        case MSG_TOAST:
            Toast.makeText(this, msg.arg1, Toast.LENGTH_SHORT).show();
            return true;
        case MSG_NO_NAME:
            showDialog(DIALOG_NAME);
            return true;
        case MSG_STATUS:
            if (msg.obj == null)
                info.setText(msg.arg1);
            else
                info.setText(msg.obj.toString());
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
        case REQUEST_PREFS:
        case REQUEST_HISTORY:
            handler.sendEmptyMessage(MSG_UPDATE);
            break;
        default:
        }
    }

    @Override
    public void onClick(View v)
    {
        if (v == send
                && (sendTask == null || sendTask.getStatus() == AsyncTask.Status.FINISHED))
            (sendTask = new SendTask(this)).execute((Object[]) null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        if (savedInstanceState != null)
            code = savedInstanceState.getString(STATE_CODE_KEY);
        setListAdapter(new ShoutBoxAdapter(this, new ArrayList<ShoutBoxItem>()));
        message = (EditText) findViewById(R.id.MessageEditText);
        send = (ImageButton) findViewById(R.id.SendButton);
        info = (TextView) findViewById(R.id.InfoTextView);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sent = true;
        send.setOnClickListener(this);
        message.setOnEditorActionListener(this);
        sleepTime = 1000;
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id)
        {
        case DIALOG_LIST:
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.diag_users_title)
                    .setNegativeButton(R.string.diag_users_button,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which)
                                {
                                    if (listTask != null
                                            && listTask.getStatus() != AsyncTask.Status.FINISHED)
                                        listTask.cancel(false);
                                }
                            }).setAdapter(
                            new ArrayAdapter<String>(getApplicationContext(),
                                    R.layout.user, new ArrayList<String>()),
                            null)
                    .create();
        case DIALOG_NAME:
            View v = getLayoutInflater().inflate(R.layout.name, null);
            final EditText name = (EditText) v.findViewById(android.R.id.text1);
            name.setText(prefs.getString(getString(R.string.pref_name_key),
                    getString(R.string.pref_name_def)));
            final EditText website = (EditText) v
                    .findViewById(android.R.id.text2);
            website.setText(prefs.getString(
                    getString(R.string.pref_website_key),
                    getString(R.string.pref_website_def)));
            return new AlertDialog.Builder(this).setTitle(
                    R.string.diag_name_title).setCancelable(false)
                    .setNeutralButton(R.string.diag_name_button,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which)
                                {
                                    String nameStr = name.getText().toString();
                                    if (nameStr.length() > 0)
                                    {
                                        prefs
                                                .edit()
                                                .putString(
                                                        getString(R.string.pref_name_key),
                                                        nameStr)
                                                .putString(
                                                        getString(R.string.pref_website_key),
                                                        website.getText()
                                                                .toString())
                                                .commit();
                                        dialog.dismiss();
                                    }
                                    else
                                    {
                                        Message.obtain(handler, MSG_TOAST,
                                                R.string.error_pref_name, 0)
                                                .sendToTarget();
                                        // TODO find a permanent solution
                                        Message.obtain(handler, MSG_NO_NAME,
                                                R.string.error_pref_name, 0)
                                                .sendToTarget();
                                    }
                                }
                            }).setView(v).create();
        default:
            return super.onCreateDialog(id);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
    {
        if (v == message)
        {
            onClick(send);
            return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        super.onListItemClick(l, v, position, id);
        final ArrayList<String> list = ((ShoutBoxItem) getListAdapter()
                .getItem(position)).urls;
        if (list.size() > 0)
        {
            final String[] urls = list.toArray(new String[0]);
            AlertDialog alert = new AlertDialog.Builder(this).setItems(urls,
                    new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse(urls[which])));
                        }
                    }).setTitle(R.string.diag_links_title).setCancelable(true)
                    .create();
            alert.setCanceledOnTouchOutside(true);
            alert.show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
        case R.id.menu_refresh:
            handler.sendEmptyMessage(MSG_UPDATE);
            break;
        case R.id.menu_users:
            showDialog(DIALOG_LIST);
            break;
        case R.id.menu_history:
            startActivityForResult(new Intent(getApplicationContext(),
                    History.class), REQUEST_HISTORY);
            break;
        case R.id.menu_pref:
            startActivity(new Intent(getApplicationContext(), Prefs.class));
            return true;
        case R.id.menu_quit:
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog)
    {
        switch (id)
        {
        case DIALOG_LIST:
            @SuppressWarnings("unchecked")
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) ((AlertDialog) dialog)
                    .getListView().getAdapter();
            adapter.clear();
            (listTask = new ListTask(this, adapter)).execute((Object[]) null);
            break;
        default:
            super.onPrepareDialog(id, dialog);
            break;
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        final String defName = getString(R.string.pref_name_def);
        if (prefs.getString(getString(R.string.pref_name_key), defName).equals(
                defName))
            showDialog(DIALOG_NAME);
        handler.sendEmptyMessage(MSG_UPDATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CODE_KEY, code);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        justOpened = true;
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        if (updateTask != null)
        {
            updateTask.cancel(true);
            updateTask = null;
        }
        handler.removeMessages(MSG_UPDATE);
        if (sendTask != null)
        {
            sendTask.cancel(true);
            sendTask = null;
        }
        if (listTask != null)
        {
            listTask.cancel(true);
            listTask = null;
        }
    }
}