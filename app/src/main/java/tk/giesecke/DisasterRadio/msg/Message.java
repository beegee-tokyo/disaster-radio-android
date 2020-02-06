package tk.giesecke.DisasterRadio.msg;

public class Message {
	private final String text; // message body
	private final String memberName;
	private final boolean belongsToCurrentUser; // is this message sent by us?

	public Message(String text, String memberName, boolean belongsToCurrentUser) {
		this.text = text;
		this.memberName = memberName;
		this.belongsToCurrentUser = belongsToCurrentUser;
	}

	String getText() {
		return text;
	}

	String getMemberData() {
		return memberName;
	}

	boolean isBelongsToCurrentUser() {
		return belongsToCurrentUser;
	}
}