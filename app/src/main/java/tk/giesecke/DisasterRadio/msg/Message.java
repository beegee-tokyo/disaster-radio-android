package tk.giesecke.DisasterRadio.msg;

/**
 * Message holds incoming messages.
 * Beside of the message itself, it holds a link to the member data
 * of the member that has sent the message.
 */
public class Message {
	private final String text; // message body
	private final MemberData memberData; // members data (name & display color)
	private final boolean belongsToCurrentUser; // is this message sent by us?

	public Message(String text, MemberData memberName, boolean belongsToCurrentUser) {
		this.text = text;
		this.memberData = memberName;
		this.belongsToCurrentUser = belongsToCurrentUser;
	}

	String getText() {
		return text;
	}

	String getColor() { return this.memberData.getColor(); }

	MemberData getMemberData() {
		return memberData;
	}

	boolean isBelongsToCurrentUser() {
		return belongsToCurrentUser;
	}
}