package tk.giesecke.DisasterRadio.msg;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import tk.giesecke.DisasterRadio.R;

/**
 * MemberAdapter is the adapter to handle the MemberData class.
 * It provides an interface to access the MemberData entries
 */
public class MemberAdapter extends BaseAdapter {
	private final List<MemberData> members = new ArrayList<>();

	private final String[] colors = {"#FFCDD2", "#B2DFDB", "#FFF9C4", "#C5CAE9",
			"#F8BBD0", "#C8E6C9", "#FFECB3", "#BBDEFB",
			"#E1BEE7", "#FFE0B2"};
	final int[] boxColors = {R.color.device_01, R.color.device_02, R.color.device_03, R.color.device_04,
			R.color.device_05, R.color.device_06, R.color.device_07, R.color.device_08,
			R.color.device_09, R.color.device_10, R.color.device_11, R.color.device_12,
			R.color.device_13, R.color.device_14, R.color.device_15, R.color.device_16,
			R.color.device_17, R.color.device_18, R.color.device_19, R.color.device_20, };

	public void add(MemberData data) {
		this.members.add((data));
		notifyDataSetChanged();
	}

	public int hasMember(String name) {
		for (int idx = 0; idx < members.size(); idx++) {
			if (getItem(idx).getName().equalsIgnoreCase(name)) {
				return idx;
			}
		}
		return -1;
	}

	public int getRandomColor() {
		int index = members.size() - 2;
		while (index > 20) {
			index -= 20;
		}
		return boxColors[index];
	}

	@Override
	public int getCount() {
		return members.size();
	}

	@Override
	public MemberData getItem(int position) {
		return members.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return null;
	}
}
