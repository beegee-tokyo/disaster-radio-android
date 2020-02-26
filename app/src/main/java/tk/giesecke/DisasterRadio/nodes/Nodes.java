package tk.giesecke.DisasterRadio.nodes;

public class Nodes {
	private String nodeId;

	public Nodes(String newNodeId) {
		super();
		this.nodeId = newNodeId;
	}

	public String get() {
		return this.nodeId;
	}

	public void set(String newName) {
		this.nodeId = newName;
	}
}
