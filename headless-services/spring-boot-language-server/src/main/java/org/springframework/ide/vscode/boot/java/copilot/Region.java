package org.springframework.ide.vscode.boot.java.copilot;

public class Region implements IRegion {

	private final int ofs;

	private final int len;

	public Region(int ofs, int len) {
		super();
		this.ofs = ofs;
		this.len = len;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Region other = (Region) obj;
		if (len != other.len) {
			return false;
		}
		if (ofs != other.ofs) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + len;
		result = prime * result + ofs;
		return result;
	}

	@Override
	public String toString() {
		return "Region [ofs=" + ofs + ", len=" + len + "]";
	}

	@Override
	public int getOffset() {
		return ofs;
	}

	@Override
	public int getLength() {
		return len;
	}

}
