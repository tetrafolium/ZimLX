package org.zimmob.zimlx.minibar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import com.android.launcher3.R;
import java.util.List;

public class DashAdapter extends BaseAdapter {

  private Context context;
  private List<DashItem> dashItems;

  public DashAdapter(final Context context, final List<DashItem> items) {
    this.context = context;
    this.dashItems = items;
  }

  public int getCount() { return dashItems.size(); }

  public Object getItem(final int item) { return dashItems.get(item); }

  public long getItemId(final int position) { return position; }

  public View getView(final int position, final View convertView,
                      final ViewGroup parent) {
    View view;
    ImageView iv;
    if (convertView == null) {
      LayoutInflater inflater = LayoutInflater.from(context);
      view = inflater.inflate(R.layout.item_minibar, parent, false);
    } else {
      view = convertView;
    }

    iv = view.findViewById(R.id.iv);
    iv.setImageResource(dashItems.get(position).icon);
    return view;
  }
}
