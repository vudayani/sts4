package org.springframework.ide.vscode.boot.java.copilot;

public class BadLocationException extends Exception {

	private static final long serialVersionUID = 4005922462747639763L;

	public BadLocationException(Throwable e) {
		super(e);
	}

	public BadLocationException(String message) {
		super(message);
	}

	public BadLocationException() {
		super();
	}

}
