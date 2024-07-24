import * as vscode from 'vscode';
import CopilotRequest from './copilotRequest';

class CopilotResultsRenderer {
    private _view?: vscode.WebviewView;

    constructor() {}

    resolveWebviewView(webviewView: vscode.WebviewView) {
        this._view = webviewView;

        webviewView.webview.options = {
            enableScripts: true,
        };

        webviewView.webview.html = this.getHtmlForWebview([]);
    }

    updateResults(newResults: string[]) {
        if (this._view) {
            this._view.webview.html = this.getHtmlForWebview(newResults);
        }
    }


    private getHtmlForWebview(results: string[]): string {
        // Convert results array to a formatted string
        const formattedResults = results.map(result => `<p>${result.replace(/\n/g, '<br>')}</p>`).join('');
        return `
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Copilot Results</title>
        </head>
        <body>
            ${formattedResults}
        </body>
        </html>
        `;
    }
}

export function activate(context: vscode.ExtensionContext) {
    const copilotResultsRenderer = new CopilotResultsRenderer();
    console.log("Copilot renderer activated");
    context.subscriptions.push(vscode.window.registerWebviewViewProvider("copilotResultsPanel", copilotResultsRenderer));
    // copilotResultsRenderer.resolveWebviewView();
    // vscode.window.registerTreeDataProvider('copilotResultsPanel', copilotResultsRenderer);
    context.subscriptions.push(vscode.commands.registerCommand('vscode-spring-boot.query.explain', async (userPrompt, range) => {
        console.log('query.explain: ' + userPrompt);
        vscode.window.showInformationMessage('query.explain executed');
        const systemPrompts: vscode.LanguageModelChatMessage[] = [
            new vscode.LanguageModelChatMessage(vscode.LanguageModelChatMessageRole.User, "Your task is to explain the user query in detail."),
            new vscode.LanguageModelChatMessage(vscode.LanguageModelChatMessageRole.User, "IMPORTANT: CONCLUDE YOUR RESPONSE WITH THE MARKER \"<|endofresponse|>\"  TO INDICATE END OF RESPONSE")
        ];
        const messages = [
            vscode.LanguageModelChatMessage.User(userPrompt)
        ];
        const copilotRequest = new CopilotRequest(systemPrompts);
        const response = await copilotRequest.chatRequest(messages, {}, null);
        console.log(response);
        copilotResultsRenderer.updateResults([response]);

        const copilot = vscode.extensions.getExtension('github.copilot-chat');
        
        // vscode.window.showInformationMessage('Response: '+ response);
        // // Your command implementation...
        // // After getting results, update the panel:
        // copilotResultsRenderer.updateResults([/* results from Copilot */]);
    }));
}