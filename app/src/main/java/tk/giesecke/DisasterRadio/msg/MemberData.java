package tk.giesecke.DisasterRadio.msg;

public class MemberData {
	private String name;
	private String color;

	public MemberData(String name, String color) {
		this.name = name;
		this.color = color;
	}

	public String getName() {
		return name;
	}

	public String getColor() {
		return color;
	}

	public void setData(String name, String color) {
		this.name = name;
		this.color = color;
	}
}
