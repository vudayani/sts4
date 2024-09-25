package org.springframework.ide.vscode.boot.java.copilot;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonElement;

public class CopilotAgentCommandHandler {

	private static final Logger log = LoggerFactory.getLogger(CopilotAgentCommandHandler.class);

	private static final String CMD_COPILOT_AGENT_ENHANCERESPONSE = "sts/copilot/agent/enhanceResponse";

	private static final String CMD_COPILOT_AGENT_LSPEDITS = "sts/copilot/agent/lspEdits";

    private final SimpleLanguageServer server;
	private final ResponseModifier responseModifier;
	

	public CopilotAgentCommandHandler(SimpleLanguageServer server, ResponseModifier responseModifier) {
		this.server = server;
		this.responseModifier = responseModifier;
		registerCommands();
	}

	private void registerCommands() {
		server.onCommand(CMD_COPILOT_AGENT_ENHANCERESPONSE, (params) -> {
			return enhanceResponseHandler(params);
		});
		log.info("Registered command handler: {}",CMD_COPILOT_AGENT_ENHANCERESPONSE);

		server.onCommand(CMD_COPILOT_AGENT_LSPEDITS, params -> {
			return computeWorkspaceEdits(params);
		});
	}

	private CompletableFuture<Object> enhanceResponseHandler(ExecuteCommandParams params) {
		log.info("Command Handler: ");
		String response = ((JsonElement) params.getArguments().get(0)).getAsString();
		String modifiedResp = responseModifier.modify(response);
        return CompletableFuture.completedFuture(response);
	}
	
	private CompletableFuture<WorkspaceEdit> computeWorkspaceEdits(ExecuteCommandParams params) {
		log.info("Command Handler for lsp edits: ");
		String content = ((JsonElement) params.getArguments().get(0)).getAsString();
		String file = ((JsonElement) params.getArguments().get(0)).getAsString();
		String path = ((JsonElement) params.getArguments().get(0)).getAsString();
        return CompletableFuture.completedFuture(null);
	}
}