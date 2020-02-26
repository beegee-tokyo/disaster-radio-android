package tk.giesecke.DisasterRadio.nodes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tk.giesecke.DisasterRadio.R;

public class NodesAdapter extends BaseAdapter {
	private final List<Nodes> nodes = new ArrayList<>();
	private final Context context;

	public NodesAdapter(Context context) {
		this.context = context;
	}

	public void add(Nodes newNode) {
		this.nodes.add(newNode);
		notifyDataSetChanged();
	}

	public void clear() {
		nodes.clear();
		this.notifyDataSetChanged();
	}

	public int hasNode(String findNodeID) {
		for (int idx = 0; idx < nodes.size(); idx++) {
			Nodes node = (Nodes)getItem(idx);
			if (node.get().equalsIgnoreCase(findNodeID)) {
				return idx;
			}
		}
		return -1;
	}

	@Override
	public int getCount() {
		return nodes.size();
	}

	@Override
	public Nodes getItem(int position) {
		return nodes.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@SuppressLint({"InflateParams", "ViewHolder"})
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		NodesViewHolder holder = new NodesViewHolder();
		LayoutInflater nodesInflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
		Nodes node = nodes.get(position);
		convertView = nodesInflater.inflate(R.layout.node_list, null);
		holder.nodes_body = convertView.findViewById(R.id.nodes_body);
		holder.nodes_body.setText(node.get());
		return convertView;
	}
}

/**
 * The holder for the data of a message
 */
class NodesViewHolder {
	public TextView name;
	TextView nodes_body;
}