package com.kwogger.rootedshoutbox;

import java.util.ArrayList;

import com.kwogger.rootedshoutbox.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

class ShoutBoxAdapter extends ArrayAdapter<ShoutBoxItem>
{
    private static class ViewHolder
    {
        TextView text1;
        TextView text2;
        TextView text3;
    }

    private LayoutInflater mInflater;
    private final int off;
    private final int on;

    // private final MovementMethod movement;

    public ShoutBoxAdapter(Context context, ArrayList<ShoutBoxItem> items)
    {
        super(context.getApplicationContext(), android.R.id.text1, items);
        mInflater = LayoutInflater.from(context);
        on = context.getResources().getColor(R.color.status_on);
        off = context.getResources().getColor(R.color.status_off);
        // movement = LinkMovementMethod.getInstance();
    }

    /**
     * Make a view to hold each row.
     * 
     * @see android.widget.ListAdapter#getView(int, android.view.View,
     *      android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder;
        if (convertView == null)
        {
            convertView = mInflater.inflate(R.layout.msg, null);
            holder = new ViewHolder();
            holder.text1 = (TextView) convertView
                    .findViewById(android.R.id.text1);
            holder.text2 = (TextView) convertView
                    .findViewById(android.R.id.text2);
            holder.text3 = (TextView) convertView.findViewById(R.id.text3);
            // holder.text1.setLinksClickable(true);
            // holder.text1.setMovementMethod(movement);
            convertView.setTag(holder);
        }
        else
            holder = (ViewHolder) convertView.getTag();
        ShoutBoxItem item = getItem(position);
        holder.text1.setText(item.content);
        holder.text2.setText(item.time);
        holder.text3.setText(item.ip);
        holder.text3.setTextColor(item.status ? on : off);
        return convertView;
    }
}
