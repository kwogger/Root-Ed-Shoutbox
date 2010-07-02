package com.kwogger.rootedshoutbox;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.util.Log;

public final class ShoutBoxItem implements Parcelable
{
    private static class SmileyImageGetter implements Html.ImageGetter
    {

        private static final URL context = getContext();

        private static final URL getContext()
        {
            try
            {
                return new URL("http://www2.shoutmix.com");
            }
            catch (MalformedURLException e)
            {
                return null;
            }
        }

        private ConcurrentHashMap<String, BitmapDrawable> map = new ConcurrentHashMap<String, BitmapDrawable>();

        @Override
        public Drawable getDrawable(String source)
        {
            if (map.containsKey(source))
            {
                BitmapDrawable bmp = map.get(source);
                return bmp;
            }
            else
            {
                InputStream is = null;
                HttpURLConnection con = null;
                try
                {
                    URL url = new URL(context, source);
                    con = (HttpURLConnection) url.openConnection();
                    con.setDoInput(true);
                    BitmapDrawable bmp = new BitmapDrawable(is = con
                            .getInputStream());
                    // TODO fix this sucker properly
                    bmp.setTargetDensity(DisplayMetrics.DENSITY_HIGH * 3 / 2);
                    bmp.setBounds(0, 0, bmp.getIntrinsicWidth(), bmp
                            .getIntrinsicHeight());
                    map.put(source, bmp);
                    return bmp;
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Error loading smiley", e);
                    return null;
                }
                finally
                {
                    if (is != null)
                        try
                        {
                            is.close();
                        }
                        catch (Exception e)
                        {
                        }
                    if (con != null)
                        try
                        {
                            con.disconnect();
                        }
                        catch (Exception e)
                        {
                        }
                }
            }
        }
    }

    public static final Parcelable.Creator<ShoutBoxItem> CREATOR = new Parcelable.Creator<ShoutBoxItem>()
    {
        public ShoutBoxItem createFromParcel(Parcel in)
        {
            Spanned content = Html.fromHtml(in.readString(), GETTER, null);
            long id = in.readLong();
            String ip = in.readString();
            String time = in.readString();
            boolean status = in.readByte() == 1;
            return new ShoutBoxItem(id, status, ip, time, content);
        }

        public ShoutBoxItem[] newArray(int size)
        {
            return new ShoutBoxItem[size];
        }
    };
    public static final Html.ImageGetter GETTER = new SmileyImageGetter();
    private static final long serialVersionUID = 1L;
    private static final String TAG = ShoutBoxItem.class.getSimpleName();
    public final Spanned content;
    public final long id;

    public final String ip;
    public boolean status;

    public final String time;

    public final ArrayList<String> urls;

    public ShoutBoxItem(long id, boolean status, String ip, String time,
            Spanned content)
    {
        this.id = id;
        this.status = status;
        this.ip = ip;
        this.time = time;
        this.content = content;
        urls = new ArrayList<String>();
        for (URLSpan url : content.getSpans(0, content.length(), URLSpan.class))
            urls.add(url.getURL());
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof ShoutBoxItem) && (((ShoutBoxItem) o).id == id);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(Html.toHtml(content));
        dest.writeLong(id);
        dest.writeString(ip);
        dest.writeString(time);
        dest.writeByte(status ? (byte) 1 : 0);
    }
}
