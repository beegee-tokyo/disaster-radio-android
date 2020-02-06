package tk.giesecke.DisasterRadio.msg;

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
import java.util.Objects;

import tk.giesecke.DisasterRadio.R;

public class MessageAdapter extends BaseAdapter {
	private final List<Message> messages = new ArrayList<>();
	private final Context context;

	public MessageAdapter(Context context) {
		this.context = context;
	}

	public void add(Message message) {
		this.messages.add(message);
		notifyDataSetChanged(); // to render the list we need to notify
	}

	public void clear() {
		while (getCount() != 0) {
			messages.remove(0);
			this.notifyDataSetChanged();
		}
	}
	@Override
	public int getCount() {
		return messages.size();
	}

	@Override
	public Object getItem(int i) {
		return messages.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	// This is the backbone of the class, it handles the creation of single ListView row (chat bubble)
	@SuppressLint("InflateParams")
	@Override
	public View getView(int i, View convertView, ViewGroup viewGroup) {
		MessageViewHolder holder = new MessageViewHolder();
		LayoutInflater messageInflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
		Message message = messages.get(i);

		if (message.isBelongsToCurrentUser()) { // this message was sent by us so let's create a basic chat bubble on the right
			convertView = Objects.requireNonNull(messageInflater).inflate(R.layout.my_message, null);
			holder.messageBody = convertView.findViewById(R.id.message_body);
			convertView.setTag(holder);
			holder.messageBody.setText(message.getText());
		} else { // this message was sent by someone else so let's create an advanced chat bubble on the left
			convertView = Objects.requireNonNull(messageInflater).inflate(R.layout.their_message, null);
			holder.name = convertView.findViewById(R.id.name);
			holder.messageBody = convertView.findViewById(R.id.message_body);
			convertView.setTag(holder);

			holder.name.setText(message.getMemberData());
			holder.messageBody.setText(message.getText());
		}

		return convertView;
	}

}

class MessageViewHolder {
	public TextView name;
	TextView messageBody;
}