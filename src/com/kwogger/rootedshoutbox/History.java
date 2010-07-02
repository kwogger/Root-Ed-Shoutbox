package com.kwogger.rootedshoutbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import com.kwogger.rootedshoutbox.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class History extends ListActivity implements Handler.Callback
{
    private static class HistoryTask extends
            AsyncTask<Object, ShoutBoxItem, Object>
    {
        private ArrayAdapter<ShoutBoxItem> adapter;
        private History self;

        public HistoryTask(History self, ArrayAdapter<ShoutBoxItem> adapter)
        {
            this.self = self;
            this.adapter = adapter;
        }

        @Override
        protected Object doInBackground(Object... params)
        {
            int pages = PreferenceManager.getDefaultSharedPreferences(
                    self.getBaseContext()).getInt(
                    self.getString(R.string.pref_pages_key), -1);
            String time = null;
            String ip = null;
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (pages > 0)
            {
                URL url;
                HttpURLConnection connection = null;
                InputStream is = null;
                BufferedReader rd = null;
                try
                {
                    for (int i = 1; i <= pages && !isCancelled(); ++i)
                    {
                        try
                        {
                            url = new URL(self.getString(R.string.url_history,
                                    self.getString(R.string.url_base))
                                    + i);
                            connection = (HttpURLConnection) url
                                    .openConnection();
                            connection.setDoInput(true);
                            connection.connect();
                            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
                            {
                                rd = new BufferedReader(new InputStreamReader(
                                        is = connection.getInputStream()), 4096);
                                String line;
                                while ((line = rd.readLine()) != null
                                        && !line.contains("id=\"messages\""))
                                    ; // do nothing
                                int index, endIndex;
                                long id = 0;
                                boolean status = false;
                                while ((line = rd.readLine()) != null
                                        && !line.contains("</ul>"))
                                {
                                    if ((index = line.indexOf("<li id=\"")) != -1)
                                    {
                                        id = Long
                                                .parseLong(line
                                                        .substring(
                                                                index + 9,
                                                                index = line
                                                                        .indexOf("\" class=\"")));
                                        status = line.contains("online");
                                    }
                                    else if ((index = line.indexOf("<cite>")) != -1)
                                        ssb
                                                .replace(0, ssb.length(), "",
                                                        0, 0)
                                                .append(
                                                        Html
                                                                .fromHtml(
                                                                        line
                                                                                .substring(
                                                                                        index + 6,
                                                                                        line
                                                                                                .indexOf("</cite>")),
                                                                        ShoutBoxItem.GETTER,
                                                                        null))
                                                .append(": ");
                                    else if ((index = line.indexOf("<q>")) != -1)
                                    {
                                        /*
                                         * TODO have a proper fix for reading in
                                         * history
                                         */
                                        endIndex = line.indexOf("</q>");
                                        ssb.append(Html.fromHtml(line
                                                .substring(index + 3,
                                                        endIndex == -1 ? line
                                                                .length()
                                                                : endIndex),
                                                ShoutBoxItem.GETTER, null));
                                    }
                                    else if ((index = line
                                            .indexOf("<dd class=\"date\">")) != -1)
                                        time = line.substring(index + 17, line
                                                .indexOf("</dd>"));
                                    else if ((index = line
                                            .indexOf("<dd title=\"")) != -1)
                                        ip = line.substring(index + 11, line
                                                .indexOf("\" class="));
                                    else if (line.contains("</li>"))
                                        publishProgress(new ShoutBoxItem(id,
                                                status, ip, time,
                                                new SpannedString(ssb)));
                                }
                                while (rd.readLine() != null)
                                    ;
                            }
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
                    }
                }
                catch (MalformedURLException e)
                {
                    self.handler.obtainMessage(MSG_FAIL,
                            R.string.error_history_url, 0).sendToTarget();
                    Log.e(TAG, "MalformedURLException when loading history", e);
                }
                catch (IOException e)
                {
                    self.handler.obtainMessage(MSG_FAIL,
                            R.string.error_history_io, 0);
                    Log.e(TAG, "IOException when loading history", e);
                }
            }
            else
                self.handler.obtainMessage(MSG_FAIL,
                        R.string.error_history_unknown, 0).sendToTarget();
            return null;
        }

        @Override
        protected void onPostExecute(Object result)
        {
            self.setProgressBarIndeterminateVisibility(false);
            self = null;
            adapter = null;
        }

        @Override
        protected void onPreExecute()
        {
            self.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected void onProgressUpdate(ShoutBoxItem... values)
        {
            if (adapter.getPosition(values[0]) == -1)
                adapter.add(values[0]);
        }
    }

    private static final int MSG_FAIL = 100;
    private static final String TAG = History.class.getSimpleName();

    private final Handler handler = new Handler(this);
    private HistoryTask task;

    @Override
    public boolean handleMessage(Message msg)
    {
        switch (msg.what)
        {
        case MSG_FAIL:
            Toast.makeText(getApplicationContext(), msg.arg1,
                    Toast.LENGTH_SHORT).show();
            finish();
            return true;
        default:
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.history);
        if (savedInstanceState == null)
        {
            ShoutBoxAdapter adapter = new ShoutBoxAdapter(this,
                    new ArrayList<ShoutBoxItem>());
            setListAdapter(adapter);
            (task = new HistoryTask(this, adapter)).execute((Object[]) null);
        }
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
    protected void onStop()
    {
        super.onStop();
        if (task != null)
            task.cancel(true);
    }
}
